// The dev server's half of edge/src/auth.ts: it answers /auth/* by running the edge's own module,
// because a second implementation is the kind that drifts silently and is found in production.
// It stops at the middleware rather than returning a Vite `Plugin`, so a dev server that resolves
// vite from another package can use it too: node:http's types are ambient, vite's are not.

import type { IncomingMessage, ServerResponse } from "node:http";

import { type AuthEnv, handleAuth, isAuthPath } from "../edge/src/auth.ts";

/** A connect-style handler; `server.middlewares.use` accepts it unchanged. */
export type DevMiddleware = (
  request: IncomingMessage,
  response: ServerResponse,
  next: (error?: unknown) => void,
) => void;

/** A route under /auth that an edge serves beside the sign-in, with the sign-in's own settings. */
export interface DevAuthRoute {
  matches(pathname: string): boolean;
  answer(request: Request, env: AuthEnv, url: URL): Promise<Response>;
}

/** The dev server's own origin, which a tunnel terminating TLS reports as https. */
const devOrigin = (request: IncomingMessage, fallbackHost: string): string => {
  const host = request.headers.host ?? fallbackHost;
  const protocol = request.headers["x-forwarded-proto"] ?? "http";
  return `${String(protocol)}://${host}`;
};

// No route reads a body, and Request refuses a GET that carries one, so none is forwarded.
const asWebRequest = (request: IncomingMessage, url: URL): Request => {
  const headers = new Headers();
  for (const [name, value] of Object.entries(request.headers)) {
    if (typeof value === "string") headers.set(name, value);
  }
  return new Request(url, { method: request.method ?? "GET", headers });
};

async function writeBack(
  answer: Response,
  response: ServerResponse,
): Promise<void> {
  for (const [name, value] of answer.headers) {
    // Iterating folds repeated Set-Cookie headers into one value, which browsers reject.
    if (name.toLowerCase() !== "set-cookie") response.setHeader(name, value);
  }
  const cookies = answer.headers.getSetCookie();
  if (cookies.length > 0) response.setHeader("Set-Cookie", cookies);
  response.statusCode = answer.status;
  response.end(await answer.text());
}

/** Serves `/auth/*` as the edge serves it: a route given here first, the sign-in otherwise. */
export function devAuthMiddleware(
  env: AuthEnv,
  fallbackHost: string,
  routes: readonly DevAuthRoute[] = [],
): DevMiddleware {
  return (request, response, next) => {
    const url = new URL(request.url ?? "/", devOrigin(request, fallbackHost));
    if (!isAuthPath(url.pathname)) {
      next();
      return;
    }
    const webRequest = asWebRequest(request, url);
    const route = routes.find((candidate) => candidate.matches(url.pathname));
    const answer =
      route === undefined
        ? handleAuth(webRequest, env, url)
        : route.answer(webRequest, env, url);
    void answer
      .then((answered) => writeBack(answered, response))
      .catch((error: unknown) => {
        next(error);
      });
  };
}
