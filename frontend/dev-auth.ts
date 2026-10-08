// The edge answers /auth/* in production rather than proxying it, so without this the dev server
// has no sign-in at all. The adaptation is dev-auth-middleware.ts; this is the configuration,
// read from the mode's env without the VITE_ prefix, so none of it can reach a bundle.

import type { Plugin } from "vite";

import type { AuthEnv } from "../edge/src/auth.ts";
import { devAuthMiddleware, type DevAuthRoute } from "./dev-auth-middleware.ts";

type DevEnv = Record<string, string | undefined>;

/** The backend the dev server proxies to, unless the mode's env names another. */
export function devBackendOrigin(env: DevEnv): string {
  return env["DEV_BACKEND_ORIGIN"] ?? "http://localhost:8080";
}

/** The dev server's port, which is also the origin the identity provider sends the browser back to. */
export function devPort(env: DevEnv): number {
  const port = env["DEV_PORT"];
  if (port === undefined) {
    return 3000;
  }
  const parsed = /^\d+$/.test(port) ? Number(port) : Number.NaN;
  if (!(parsed >= 1 && parsed <= 65_535)) {
    throw new Error(`DEV_PORT must be a port number, got "${port}"`);
  }
  return parsed;
}

/** Cookies are not port-scoped, so two dev servers on localhost with one prefix sign each other out. */
export function devCookiePrefix(env: DevEnv): string {
  return env["DEV_COOKIE_NAME_PREFIX"] ?? "axonia";
}

/** The scopes every sign-in asks for (AuthEnv.OIDC_SCOPES). */
export function devScopes(env: DevEnv): string {
  return env["DEV_OIDC_SCOPES"] ?? "openid profile email";
}

/** How a sign-out ends the session at the identity provider (AuthEnv.OIDC_SESSION_END). */
export function devSessionEnd(env: DevEnv): AuthEnv["OIDC_SESSION_END"] {
  const sessionEnd = env["DEV_OIDC_SESSION_END"] ?? "refresh-token-post";
  if (sessionEnd !== "refresh-token-post" && sessionEnd !== "revoke") {
    throw new Error(
      `DEV_OIDC_SESSION_END must be refresh-token-post or revoke, got "${sessionEnd}"`,
    );
  }
  return sessionEnd;
}

/**
 * Serves `/auth/*` from the dev server exactly as the edge serves it in production, with the routes
 * an edition's edge serves beside the sign-in. The two secrets are checked-in local constants: they
 * protect nothing here, they only make the local request path identical in shape to production.
 */
export function devAuth(
  env: Record<string, string>,
  routes: readonly DevAuthRoute[] = [],
): Plugin {
  const auth: AuthEnv = {
    OIDC_ISSUER: env["DEV_OIDC_ISSUER"] ?? "",
    OIDC_CLIENT_ID: env["DEV_OIDC_CLIENT_ID"] ?? "",
    OIDC_CLIENT_SECRET: "local-dev-client-secret",
    SESSION_COOKIE_KEY: "A".repeat(43),
    // Sixty days, written as literal seconds because a check outside this tree reads this line
    // against the edge's own setting; a string, because AuthEnv carries the edge's vars as strings.
    SESSION_MAX_AGE_SECONDS: "5184000",
    COOKIE_NAME_PREFIX: devCookiePrefix(env),
    OIDC_SCOPES: devScopes(env),
    OIDC_SESSION_END: devSessionEnd(env),
  };

  return {
    name: "axonia-dev-auth",
    configureServer(server) {
      server.middlewares.use(
        devAuthMiddleware(auth, `localhost:${String(devPort(env))}`, routes),
      );
    },
  };
}
