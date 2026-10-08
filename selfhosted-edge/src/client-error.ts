import type { ServerResponse } from "node:http";
import type { Duplex } from "node:stream";
import type { EdgeConfiguration } from "./config.ts";
import { withPolicies } from "./policies.ts";
import { problemDocument } from "./respond.ts";

interface Refusal {
  status: number;
  title: string;
  detail: string;
}

// The answers Node gives a request it cannot parse (node:http's socketOnError), each written here
// under the headers every other answer of the edge's carries.
const REFUSALS: Readonly<Record<string, Refusal>> = {
  HPE_HEADER_OVERFLOW: {
    status: 431,
    title: "Request Header Fields Too Large",
    detail: "The request's headers are too large.",
  },
  HPE_CHUNK_EXTENSIONS_OVERFLOW: {
    status: 413,
    title: "Payload Too Large",
    detail: "The request's chunk extensions are too large.",
  },
  ERR_HTTP_REQUEST_TIMEOUT: {
    status: 408,
    title: "Request Timeout",
    detail: "The request took too long to arrive.",
  },
};
const MALFORMED: Refusal = {
  status: 400,
  title: "Bad Request",
  detail: "The request could not be read.",
};

// An answer written into the middle of one already on its way would corrupt both, so the edge
// answers only a connection whose response, if any, has not started. Node reads the same field.
const isAnswering = (socket: Duplex): boolean =>
  (socket as Duplex & { _httpMessage?: ServerResponse | null })._httpMessage?.headersSent === true;

const refusalFor = (code: string | undefined, configuration: EdgeConfiguration): string => {
  const refusal = REFUSALS[code ?? ""] ?? MALFORMED;
  const body = problemDocument(refusal);
  const headers = withPolicies(
    {
      "content-type": "application/problem+json",
      "content-length": Buffer.byteLength(body),
      "cache-control": "no-store",
      connection: "close",
    },
    "/",
    configuration,
  );
  const lines = Object.entries(headers).map(([name, value]) => `${name}: ${String(value)}\r\n`);
  return `HTTP/1.1 ${refusal.status} ${refusal.title}\r\n${lines.join("")}\r\n${body}`;
};

/** Answers a request Node could not parse, then closes its connection, as Node's default does. */
export const answerClientError =
  (configuration: EdgeConfiguration) =>
  (error: NodeJS.ErrnoException, socket: Duplex): void => {
    if (socket.writable && !isAnswering(socket)) {
      socket.write(refusalFor(error.code, configuration));
    }
    socket.destroy();
  };
