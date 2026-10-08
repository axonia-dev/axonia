import type { Exchange } from "./respond.ts";

// One JSON line per failure on stderr, named by the same strings the Cloud edge's spans use, so a
// log query written for one edition reads the other. The path never includes the query: a sign-in
// callback's query carries the authorization code.
const writeLine = (failure: string, exchange: Exchange, detail: Record<string, unknown>): void => {
  const line = {
    level: "error",
    failure,
    method: exchange.request.method,
    path: exchange.url.pathname,
    ...detail,
  };
  process.stderr.write(`${JSON.stringify(line)}\n`);
};

export const logFailure = (failure: string, exchange: Exchange, error?: unknown): void => {
  writeLine(
    failure,
    exchange,
    error instanceof Error ? { error: { name: error.name, message: error.message } } : {},
  );
};

// A failure the auth module reports through its port. The provider's RFC 6749 code sits under its
// own key: `error` is an exception's name and message on every other line, and a log store that
// types its fields would drop a line holding a string there.
export const logAuthFailure = (failure: string, exchange: Exchange, oauthError?: string): void => {
  writeLine(failure, exchange, oauthError === undefined ? {} : { oauth_error: oauthError });
};
