// Test support shared by the suites that drive the OAuth client: an identity provider that answers
// discovery the way a real one does, and the cookie readers every one of those suites needs.
// Not a test file itself, so vitest never collects it, and never imported by a Worker entry point.

import { type Mock, vi } from "vitest";

export const DISCOVERY_PATH = "/.well-known/openid-configuration";

export interface DiscoveryDocument {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  end_session_endpoint?: string | undefined;
  revocation_endpoint?: string | undefined;
}

/**
 * What Keycloak advertises for a realm. Every URL here is the one auth.ts used to build by hand
 * before it asked, so a suite that serves this is proof the Cloud edition's behaviour did not move.
 */
export const keycloakLike = (issuer: string): DiscoveryDocument => ({
  issuer,
  authorization_endpoint: `${issuer}/protocol/openid-connect/auth`,
  token_endpoint: `${issuer}/protocol/openid-connect/token`,
  end_session_endpoint: `${issuer}/protocol/openid-connect/logout`,
  revocation_endpoint: `${issuer}/protocol/openid-connect/revoke`,
});

/** What Microsoft Entra ID advertises for one tenant: no revocation endpoint at all. */
export const ENTRA_ISSUER = "https://login.microsoftonline.com/a-tenant/v2.0";

export const ENTRA_LIKE = {
  issuer: ENTRA_ISSUER,
  authorization_endpoint: "https://login.microsoftonline.com/a-tenant/oauth2/v2.0/authorize",
  token_endpoint: "https://login.microsoftonline.com/a-tenant/oauth2/v2.0/token",
  end_session_endpoint: "https://login.microsoftonline.com/a-tenant/oauth2/v2.0/logout",
} satisfies DiscoveryDocument;

const targetOf = (input: unknown): string => (input instanceof Request ? input.url : String(input));

/**
 * A Keycloak-shaped discovery answer for whichever issuer `target` asks about, or `undefined` when
 * `target` is not a discovery request. For suites that stub fetch per test with their own spy.
 */
export const keycloakDiscovery = (input: unknown): Response | undefined => {
  const target = targetOf(input);
  return target.endsWith(DISCOVERY_PATH)
    ? Response.json(keycloakLike(target.slice(0, -DISCOVERY_PATH.length)))
    : undefined;
};

export type Fetch = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

/**
 * A document to serve, or a function producing the answer from the request's init, for an outage,
 * a timeout or a malformed document.
 */
type Served = DiscoveryDocument | ((init?: RequestInit) => Response | Promise<Response>);

export interface IdentityProviderStub {
  /** Every discovery URL fetched, in order. */
  readonly discoveryRequests: string[];
  serve(issuer: string, served: Served): void;
}

/**
 * Replaces the global fetch: discovery requests are answered from what `serve` registered, and
 * every other call goes to `outbound`, so a suite's own spy sees exactly the calls it saw before
 * discovery existed and none of its positional assertions move.
 */
export function stubIdentityProvider(outbound: Mock<Fetch>): IdentityProviderStub {
  const served = new Map<string, Served>();
  const discoveryRequests: string[] = [];
  vi.stubGlobal("fetch", async (input: string | URL | Request, init?: RequestInit) => {
    const target = targetOf(input);
    if (!target.endsWith(DISCOVERY_PATH)) {
      return outbound(input, init);
    }
    discoveryRequests.push(target);
    const answer = served.get(target.slice(0, -DISCOVERY_PATH.length));
    if (answer === undefined) {
      return new Response(null, { status: 404 });
    }
    return typeof answer === "function" ? answer(init) : Response.json(answer);
  });
  return {
    discoveryRequests,
    serve: (issuer, answer) => {
      served.set(issuer, answer);
    },
  };
}

/** Every Set-Cookie on a response, which cannot be read off Headers.get as one value. */
export const setCookies = (response: Response): string[] => response.headers.getSetCookie();

export const cookieNamed = (response: Response, name: string): string | undefined =>
  setCookies(response).find((cookie) => cookie.startsWith(`${name}=`));

/** The value of a Set-Cookie, i.e. what the browser would send back. */
export const valueOf = (cookie: string): string =>
  cookie.slice(cookie.indexOf("=") + 1, cookie.indexOf(";"));

export const tokenResponse = (body: Record<string, unknown>, status = 200): Response =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
