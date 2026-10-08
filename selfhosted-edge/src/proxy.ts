import { once } from "node:events";
import {
  Agent as HttpAgent,
  type ClientRequest,
  type IncomingHttpHeaders,
  type IncomingMessage,
  type OutgoingHttpHeaders,
  request as httpRequest,
} from "node:http";
import { Agent as HttpsAgent, request as httpsRequest } from "node:https";
import { pipeline } from "node:stream/promises";
import { clientAddress } from "./client-address.ts";
import { logFailure } from "./log.ts";
import { isMediaPath } from "./policies.ts";
import { type Exchange, sendProblem, writeHead } from "./respond.ts";
import { CLIENT_IP_HEADER, EDGE_AUTH_HEADER, ORIGIN_UNREACHABLE } from "./shared.ts";

// The Cloud edge's list less its payment webhook: this edition takes no payments, so it receives no
// payment webhook. The operator API, /ops, is absent from both lists.
const BACKEND_PATHS = [
  "/axonia",
  "/public",
  "/mcp",
  "/scim",
  "/oauth/token",
  "/oauth/revoke",
  "/.well-known/oauth-protected-resource",
  "/.well-known/oauth-authorization-server",
];

export const isBackendPath = (pathname: string): boolean =>
  BACKEND_PATHS.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));

// RFC 9110 section 7.6.1: these describe one connection, not the message, so none crosses the edge
// in either direction, and neither does any header the Connection header names.
const HOP_BY_HOP = [
  "connection",
  "keep-alive",
  "proxy-connection",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
];

// What only the edge may say about a request. The backend reads the two stamps alone; the rest is
// dropped rather than passed on, so no later change on the backend can come to trust a header the
// caller wrote.
const EDGE_OWNED = [
  CLIENT_IP_HEADER.toLowerCase(),
  EDGE_AUTH_HEADER.toLowerCase(),
  "forwarded",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-port",
  "x-forwarded-proto",
  "x-real-ip",
];

const NOTHING: ReadonlySet<string> = new Set();
// Content-Length is written from the parsed message (bodyFraming), never copied from the map.
const NOT_FORWARDED: ReadonlySet<string> = new Set([
  "host",
  "expect",
  "content-length",
  ...EDGE_OWNED,
]);
// An uploaded image is fetched by an img element, and the member's session is no business of the
// request that serves it, as on the Cloud edge (edge/src/media.ts).
const NOT_FORWARDED_TO_MEDIA: ReadonlySet<string> = new Set([
  ...NOT_FORWARDED,
  "cookie",
  "authorization",
]);

// A pooled socket is dropped after fifteen idle seconds, before the backend's own keep-alive
// timeout can close it under a request that has just been written to it.
const AGENT_OPTIONS = { keepAlive: true, timeout: 15_000 };
const httpAgent = new HttpAgent(AGENT_OPTIONS);
const httpsAgent = new HttpsAgent(AGENT_OPTIONS);

const connectionTokens = (headers: IncomingHttpHeaders): string[] =>
  (headers.connection ?? "")
    .split(",")
    .map((token) => token.trim().toLowerCase())
    .filter((token) => token !== "");

export const endToEnd = (
  headers: IncomingHttpHeaders,
  dropped: ReadonlySet<string> = NOTHING,
): OutgoingHttpHeaders => {
  const excluded = new Set([...HOP_BY_HOP, ...connectionTokens(headers), ...dropped]);
  return Object.fromEntries(
    Object.entries(headers).filter(([name, value]) => value !== undefined && !excluded.has(name)),
  );
};

// How the body is delimited is the edge's to write, from the message Node parsed and validated,
// never from the header map the caller shaped: Node chunks a body on GET, HEAD, DELETE, OPTIONS or
// TRACE only when told to, and an unframed body is read by the backend as the next request on a
// pooled connection, which the next caller's request then completes under its own credentials.
// The length is the number Node parsed, written without leading zeros: exact past 2^53, where
// Number would round it.
const bodyFraming = (request: IncomingMessage): OutgoingHttpHeaders | null => {
  const { "transfer-encoding": transferEncoding, "content-length": contentLength } =
    request.headers;
  if (transferEncoding !== undefined) {
    return { "transfer-encoding": "chunked" };
  }
  return contentLength === undefined
    ? null
    : { "content-length": BigInt(contentLength).toString() };
};

// Node's parser refuses a request whose last coding is not chunked, but takes one before it, gzip
// say. The edge decodes no coding, so it would hand the backend gzip bytes as a body with none.
// Refused rather than forwarded (RFC 9112 section 6.1): the outbound framing stays one value the
// edge writes, not a list Node's outgoing check, its parser and the backend's must read alike.
const hasForeignCoding = (request: IncomingMessage): boolean => {
  const transferEncoding = request.headers["transfer-encoding"];
  return transferEncoding !== undefined && transferEncoding.toLowerCase() !== "chunked";
};

export const backendHeaders = (exchange: Exchange): OutgoingHttpHeaders => {
  const { request, url, configuration } = exchange;
  const dropped = isMediaPath(url.pathname) ? NOT_FORWARDED_TO_MEDIA : NOT_FORWARDED;
  return {
    ...endToEnd(request.headers, dropped),
    ...bodyFraming(request),
    [CLIENT_IP_HEADER]: clientAddress({
      socketAddress: request.socket.remoteAddress,
      // Every X-Forwarded-For line in the order received: a proxy may add a line of its own.
      forwardedFor: request.headersDistinct["x-forwarded-for"]?.join(","),
      trustedProxyHops: configuration.trustedProxyHops,
    }),
    [EDGE_AUTH_HEADER]: configuration.edgeSharedSecret,
  };
};

// The path is the normalised one the routing decision was made on, so a dot segment cannot route a
// request past isBackendPath and then mean something else at the backend.
const openBackendRequest = (exchange: Exchange): ClientRequest => {
  const { backendOrigin } = exchange.configuration;
  const secure = backendOrigin.protocol === "https:";
  return (secure ? httpsRequest : httpRequest)({
    hostname: backendOrigin.hostname.replace(/^\[(.*)\]$/, "$1"),
    ...(backendOrigin.port === "" ? {} : { port: backendOrigin.port }),
    method: exchange.request.method,
    path: `${exchange.url.pathname}${exchange.url.search}`,
    headers: backendHeaders(exchange),
    agent: secure ? httpsAgent : httpAgent,
  });
};

export const proxyToBackend = async (exchange: Exchange): Promise<void> => {
  const { request, response } = exchange;
  if (hasForeignCoding(request)) {
    sendProblem(exchange, {
      status: 501,
      title: "Not Implemented",
      detail: "No transfer coding but chunked is accepted.",
    });
    return;
  }
  const outbound = openBackendRequest(exchange);
  // Seen through once() before the answer and through the answer's pipeline after it. An error
  // event nobody listens to would end the process, and a backend reset mid-answer emits one.
  outbound.on("error", () => undefined);
  // A caller who leaves takes the backend request with them, an event stream on /mcp above all.
  response.once("close", () => {
    if (!response.writableFinished) {
      outbound.destroy();
    }
  });
  // A message with no framing has no body, so nothing the caller sends after its head is written.
  if (bodyFraming(request) === null) {
    outbound.end();
  } else {
    request.pipe(outbound);
  }

  let answer: IncomingMessage;
  try {
    [answer] = (await once(outbound, "response")) as [IncomingMessage];
  } catch (error) {
    if (!response.destroyed) {
      logFailure(ORIGIN_UNREACHABLE, exchange, error);
      sendProblem(exchange, {
        status: 502,
        title: "Bad Gateway",
        detail: "The backend is unreachable.",
      });
    }
    return;
  }
  writeHead(exchange, answer.statusCode ?? 502, endToEnd(answer.headers));
  await pipeline(answer, response);
};
