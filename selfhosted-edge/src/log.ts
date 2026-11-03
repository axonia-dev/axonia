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

interface LoggedError {
  name: string;
  message: string;
  code?: string;
  cause?: LoggedError;
}

// A failed fetch says only "fetch failed": the reason, such as ECONNREFUSED, is in its cause.
const CAUSE_DEPTH = 3;

const loggedError = (error: Error, depth = 0): LoggedError => {
  const { code } = error as { code?: unknown };
  return {
    name: error.name,
    message: error.message,
    ...(typeof code === "string" ? { code } : {}),
    ...(error.cause instanceof Error && depth < CAUSE_DEPTH
      ? { cause: loggedError(error.cause, depth + 1) }
      : {}),
  };
};

export const logFailure = (failure: string, exchange: Exchange, error?: unknown): void => {
  writeLine(failure, exchange, error instanceof Error ? { error: loggedError(error) } : {});
};

// A failure the auth module reports through its port. The provider's RFC 6749 code sits under its
// own key: `error` is an exception's name and message on every other line, and a log store that
// types its fields would drop a line holding a string there.
export const logAuthFailure = (failure: string, exchange: Exchange, oauthError?: string): void => {
  writeLine(failure, exchange, oauthError === undefined ? {} : { oauth_error: oauthError });
};
