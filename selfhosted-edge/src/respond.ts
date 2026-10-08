import type { IncomingMessage, OutgoingHttpHeaders, ServerResponse } from "node:http";
import type { EdgeConfiguration } from "./config.ts";
import { withPolicies } from "./policies.ts";

export interface Exchange {
  request: IncomingMessage;
  response: ServerResponse;
  /** The request target read against PUBLIC_URL, never against the Host header. */
  url: URL;
  configuration: EdgeConfiguration;
}

interface Problem {
  status: number;
  title: string;
  detail: string;
  headers?: OutgoingHttpHeaders;
}

export const writeHead = (
  exchange: Exchange,
  status: number,
  headers: OutgoingHttpHeaders,
): void => {
  exchange.response.writeHead(
    status,
    withPolicies(headers, exchange.url.pathname, exchange.configuration),
  );
};

export const problemDocument = ({ status, title, detail }: Problem): string =>
  JSON.stringify({ type: "about:blank", title, status, detail });

export const sendProblem = (exchange: Exchange, problem: Problem): void => {
  const body = problemDocument(problem);
  writeHead(exchange, problem.status, {
    ...problem.headers,
    "content-type": "application/problem+json",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
  });
  exchange.response.end(exchange.request.method === "HEAD" ? undefined : body);
};

// The auth module answers with a fetch Response. Its Set-Cookie headers are read one by one: the
// Headers iterator joins them with a comma, which is a valid Expires date's own separator.
export const sendWebResponse = async (exchange: Exchange, answer: Response): Promise<void> => {
  const headers: OutgoingHttpHeaders = {};
  answer.headers.forEach((value, name) => {
    if (name !== "set-cookie") {
      headers[name] = value;
    }
  });
  const cookies = answer.headers.getSetCookie();
  if (cookies.length > 0) {
    headers["set-cookie"] = cookies;
  }
  const body = Buffer.from(await answer.arrayBuffer());
  writeHead(exchange, answer.status, { ...headers, "content-length": body.byteLength });
  exchange.response.end(body);
};
