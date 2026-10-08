import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { answerClientError } from "./client-error.ts";
import type { EdgeConfiguration } from "./config.ts";
import { logAuthFailure, logFailure } from "./log.ts";
import { isBackendPath, proxyToBackend } from "./proxy.ts";
import { type Exchange, sendProblem, sendWebResponse, writeHead } from "./respond.ts";
import { handleAuth, IDENTITY_PROVIDER_UNREACHABLE, isAuthPath } from "./shared.ts";
import { sendShell, serveStatic } from "./static.ts";

const HEALTH_PATH = "/healthz";
const AUTH_METHODS: ReadonlySet<string> = new Set(["GET", "POST"]);
const STATIC_METHODS: ReadonlySet<string> = new Set(["GET", "HEAD"]);
// Longer than the sixty seconds a load balancer in front commonly keeps an idle connection, so the
// balancer closes it, never this end while the balancer is sending a request down it.
const KEEP_ALIVE_TIMEOUT_MS = 65_000;
const EDGE_ERROR = "edge_error";

// The request target read against PUBLIC_URL, never against the Host header, which the caller
// writes: the sign-in's redirect_uri and every redirect the auth module builds come from this URL.
// Concatenated rather than resolved, so "//host/path" stays a path on this origin. A target that is
// not a path at all (an absolute URL, or "*") is null.
export const publicUrlOf = (target: string | undefined, publicOrigin: string): URL | null =>
  target?.startsWith("/") ? new URL(`${publicOrigin}${target}`) : null;

const isNavigation = (request: IncomingMessage): boolean =>
  request.method === "GET" && (request.headers.accept ?? "").includes("text/html");

// The auth module reads a request's method and cookies, and nothing else of it.
const toWebRequest = (exchange: Exchange): Request => {
  const { cookie } = exchange.request.headers;
  return new Request(exchange.url, {
    method: exchange.request.method ?? "GET",
    headers: cookie === undefined ? {} : { cookie },
  });
};

const sendHealth = (exchange: Exchange): void => {
  writeHead(exchange, 200, {
    "content-type": "text/plain; charset=utf-8",
    "content-length": 2,
    "cache-control": "no-store",
  });
  exchange.response.end(exchange.request.method === "HEAD" ? undefined : "ok");
};

const authenticate = async (exchange: Exchange): Promise<void> => {
  exchange.request.resume();
  // Refused here and not left to the auth module: fetch's Request throws on CONNECT and TRACE.
  if (!AUTH_METHODS.has(exchange.request.method ?? "")) {
    sendProblem(exchange, {
      status: 405,
      title: "Method Not Allowed",
      detail: "That method is not accepted on this route.",
      headers: { allow: "GET, POST" },
    });
    return;
  }
  const answer = await handleAuth(
    toWebRequest(exchange),
    {
      ...exchange.configuration.auth,
      reportFailure: (failure, oauthError) => {
        logAuthFailure(failure, exchange, oauthError);
      },
    },
    exchange.url,
  );
  // The auth module answers 503 for one thing alone: an identity provider it could not reach.
  if (answer.status === 503) {
    logFailure(IDENTITY_PROVIDER_UNREACHABLE, exchange);
  }
  await sendWebResponse(exchange, answer);
};

const serveApp = async (exchange: Exchange): Promise<void> => {
  if (!STATIC_METHODS.has(exchange.request.method ?? "")) {
    sendProblem(exchange, {
      status: 405,
      title: "Method Not Allowed",
      detail: "That method is not accepted on this route.",
      headers: { allow: "GET, HEAD" },
    });
    return;
  }
  if (await serveStatic(exchange)) {
    return;
  }
  if (isNavigation(exchange.request)) {
    await sendShell(exchange);
    return;
  }
  sendProblem(exchange, {
    status: 404,
    title: "Not Found",
    detail: "Nothing is served at this address.",
  });
};

// The order is the contract: the health check, then sign-in, then the backend, then the app. A
// route of the app's that began with a backend prefix would never be reached.
const route = async (exchange: Exchange): Promise<void> => {
  const { pathname } = exchange.url;
  if (pathname === HEALTH_PATH) {
    sendHealth(exchange);
  } else if (isAuthPath(pathname)) {
    await authenticate(exchange);
  } else if (isBackendPath(pathname)) {
    await proxyToBackend(exchange);
  } else {
    await serveApp(exchange);
  }
};

const handle = async (
  configuration: EdgeConfiguration,
  request: IncomingMessage,
  response: ServerResponse,
): Promise<void> => {
  const url = publicUrlOf(request.url, configuration.publicOrigin);
  const exchange: Exchange = {
    request,
    response,
    configuration,
    url: url ?? new URL(configuration.publicOrigin),
  };
  try {
    if (url === null) {
      sendProblem(exchange, {
        status: 400,
        title: "Bad Request",
        detail: "The request target must be a path.",
      });
      return;
    }
    await route(exchange);
  } catch (error) {
    if (response.headersSent) {
      response.destroy();
      return;
    }
    logFailure(EDGE_ERROR, exchange, error);
    sendProblem(exchange, {
      status: 500,
      title: "Internal Server Error",
      detail: "The edge could not answer this request.",
    });
  }
};

export const createEdgeServer = (configuration: EdgeConfiguration): Server => {
  const server = createServer((request, response) => {
    void handle(configuration, request, response);
  });
  server.on("clientError", answerClientError(configuration));
  server.keepAliveTimeout = KEEP_ALIVE_TIMEOUT_MS;
  server.headersTimeout = KEEP_ALIVE_TIMEOUT_MS + 1_000;
  return server;
};
