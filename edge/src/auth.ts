// The Worker is the OAuth client. The browser starts a login by navigating here, comes back here,
// and afterwards asks here for a fresh access token; at no point does it hold a credential that
// outlives the tab, and at no point does it talk to the identity provider with XHR.
//
// What that buys: the sixty-day credential moves from localStorage, where any script on the
// origin can read it, into a sealed HttpOnly cookie, and the client becomes confidential, so the
// refresh token is useless to anyone who does obtain it without also holding the client secret.
//
// Nothing here assumes Keycloak. The endpoints come from the issuer's discovery document, and the
// two behaviours that are Keycloak's own - the scopes a login asks for and how a session is ended
// at the provider - are settings. An edge may serve routes of its own under /auth beside these;
// this module answers 404 for every path it does not know.

import {
  clearedCookie,
  cookieHeader,
  oauthCookieName,
  readCookie,
  seal,
  sealingKey,
  sessionCookieName,
  unseal,
} from "./sealed-cookie.ts";

export const AUTH_PATH = "/auth";

export interface AuthEnv {
  /** The issuer, e.g. https://login.example.com/realms/example. Its endpoints are discovered. */
  OIDC_ISSUER: string;
  OIDC_CLIENT_ID: string;
  /** A deployment secret. Its whole purpose is to never reach the browser. */
  OIDC_CLIENT_SECRET: string;
  /** A deployment secret: 32 bytes, base64url. */
  SESSION_COOKIE_KEY: string;
  /**
   * How long the session cookie lives, re-stamped on every rotation so it slides exactly as the
   * identity provider's session does. Spelled as a string because an edge's settings arrive as
   * strings and this same interface is built in TypeScript by the dev servers - a `number` field
   * would make one of those call sites lie. Parsed and validated in handleAuth, which is also where
   * the reasoning for having no default lives.
   */
  SESSION_MAX_AGE_SECONDS: string;
  /**
   * The configurable part of every cookie name, one per application signing in on the same host.
   * See sealed-cookie.ts for why the `__Host-` prefix itself is never part of this value.
   */
  COOKIE_NAME_PREFIX: string;
  /**
   * The space-separated scopes every login asks for, with an `organization:<alias>` appended when
   * the member asked for one. Keycloak needs only `openid profile email`; Entra ID issues no
   * refresh token without `offline_access`, and without a scope of axonia's own it issues an access
   * token for Microsoft Graph that axonia cannot verify.
   */
  OIDC_SCOPES: string;
  /**
   * How a sign-out ends the session at the identity provider, besides the browser's redirect to the
   * end-session endpoint that happens either way. `refresh-token-post` posts the refresh token to
   * the end-session endpoint, Keycloak's extension, which ends its SSO session. `revoke` revokes
   * the refresh token at the RFC 7009 revocation endpoint when the issuer advertises one.
   */
  OIDC_SESSION_END: "refresh-token-post" | "revoke";
  /**
   * How this module, and any flow an edge builds beside it, reports a failure whose cause the
   * response cannot carry, as a port each entry point fills in: the Cloud's edge puts it on the
   * request's span, the Self-hosted edge writes a log line, and the dev server supplies nothing. A
   * port rather than a direct `emitSpan` call, because `emitSpan` takes workerd's
   * `ExecutionContext` and these modules are also compiled for Node and by the Vite dev server,
   * whose types do not carry it.
   *
   * `failure` is one of the strings an edge's alerting matches. This module passes
   * TOKEN_REFUSED, for a restarted sign-in or a renewal refused as `invalid_client` or
   * `unauthorized_client` (a sign-in also with no readable code), and NO_REFRESH_TOKEN, for a
   * restarted sign-in granted without a refresh token: each means the edge's own client settings
   * are wrong, and a renewal so refused answers the same 401 as a member's spent grant. A code the
   * caller can cause, such as `invalid_grant`, is never reported. `oauthError` is the
   * provider's RFC 6749 error code when it sent a well-formed one, and never anything else from its
   * body.
   */
  reportFailure?: (failure: string, oauthError?: string) => void;
}

/**
 * The ceiling `SESSION_MAX_AGE_SECONDS` may declare: two years. It comfortably exceeds any
 * lifetime a deployment would choose, but it exists to catch a fat-fingered deployment, not to
 * express a policy anyone wants: a value large enough switches `Number()` to exponent notation
 * (`Max-Age=1e+22`), which is not a valid Max-Age at all, so the browser discards it and silently
 * turns a persistent session cookie into one that dies at browser close. That failure produces a
 * green gate and a config file that reads as entirely reasonable, which is exactly the shape of
 * mistake this field's validation exists to catch.
 */
const MAX_SESSION_MAX_AGE_SECONDS = 63072000;

/**
 * `SESSION_MAX_AGE_SECONDS`, read off configuration rather than hard-coded: two applications
 * signing in through one identity provider can genuinely need different lifetimes, sixty days
 * against thirty minutes, and a cookie outliving the session strands a member on a refresh that
 * can only fail while a cookie expiring first signs out somebody the identity provider still
 * considers present.
 * Either mistake is invisible until somebody hits it, which is exactly why there is no fallback
 * here: a default sixty days is what would quietly hand a thirty-minute realm the wrong lifetime.
 * Absent, non-numeric, non-positive, or larger than MAX_SESSION_MAX_AGE_SECONDS all answer `null`,
 * so the caller can fail the way a missing secret already does rather than starting a Worker that
 * would silently mismanage every session.
 */
const parsedSessionMaxAgeSeconds = (raw: string): number | null => {
  if (!/^[1-9][0-9]*$/.test(raw)) {
    return null;
  }
  const parsed = Number(raw);
  return parsed <= MAX_SESSION_MAX_AGE_SECONDS ? parsed : null;
};

/**
 * `OIDC_SCOPES` must be single-space-separated scope tokens and include `openid`, without which the
 * provider is doing plain OAuth and returns no ID token. No default, for the reason
 * SESSION_MAX_AGE_SECONDS has none: a default is the Keycloak value, and handing it silently to an
 * Entra deployment is a login that works and then never renews.
 */
const isUsableScopes = (raw: string): boolean =>
  /^\S+( \S+)*$/.test(raw) && raw.split(" ").includes("openid");

const SESSION_END_MODES: ReadonlySet<string> = new Set(["refresh-token-post", "revoke"]);

/**
 * Ten minutes to get through the identity provider's login form, which is generous for a password
 * and enough for a round trip out to Google or a corporate identity provider and back. It is
 * deliberately short: this cookie holds the PKCE verifier, and a verifier that outlives the
 * authorization code it protects is a verifier waiting to be paired with a stolen one.
 */
export const OAUTH_MAX_AGE_SECONDS = 10 * 60;

/** What the session cookie holds once opened. */
interface SessionState {
  rt: string;
  /**
   * The scope this session was established with. It lives here rather than in the SPA because a
   * refresh grant can only ever narrow scope, never widen it: an `organization:<alias>` scope has
   * to ride the authorize request, so every renew must re-request the exact scope the login asked
   * for or the `org` claim silently disappears from the next token.
   */
  scope: string;
}

/** What the handoff cookie holds between the redirect out to the provider and the redirect back. */
interface OAuthState {
  state: string;
  verifier: string;
  returnTo: string;
  scope: string;
}

// ------------------------------------------------------------------------------------------------
// Discovery
// ------------------------------------------------------------------------------------------------

/** The endpoints this module uses, as the issuer advertises them. */
export interface OidcEndpoints {
  authorization: string;
  token: string;
  endSession: string | null;
  revocation: string | null;
}

const DISCOVERY_PATH = "/.well-known/openid-configuration";

/**
 * How long any request to the identity provider may take: discovery, the token endpoint and the
 * end of a session. A provider that accepts the connection and never answers would otherwise hold
 * the member's request until the runtime gives up on it. Five seconds is far beyond a healthy
 * provider's answer and well inside a browser's patience. Nothing about a timeout is remembered,
 * so the next request asks again.
 */
const PROVIDER_TIMEOUT_MS = 5000;

const LOCAL_HOSTNAMES: ReadonlySet<string> = new Set(["localhost", "127.0.0.1", "[::1]"]);

/**
 * An issuer the edge may ask for its endpoints, or an endpoint it may send a client secret or a
 * browser to: https, or http on this machine alone, which is what local development runs against.
 * A cleartext token endpoint anywhere else would carry the client secret and every refresh token in
 * the open, and a cleartext issuer would let anybody on the path answer discovery with endpoints of
 * their own choosing.
 */
const isUsableEndpoint = (raw: unknown): raw is string => {
  if (typeof raw !== "string" || !URL.canParse(raw)) {
    return false;
  }
  const { protocol, hostname } = new URL(raw);
  return protocol === "https:" || (protocol === "http:" && LOCAL_HOSTNAMES.has(hostname));
};

const required = (document: Record<string, unknown>, name: string): string => {
  const value = document[name];
  if (!isUsableEndpoint(value)) {
    throw new Error(`The discovery document's ${name} is missing or unusable.`);
  }
  return value;
};

/**
 * Absent is fine: Entra advertises no revocation endpoint, and a provider may advertise no
 * end-session endpoint. Published as JSON `null` is the same statement. Present and unusable is
 * not, because this module would otherwise redirect a browser to it or post a refresh token to it.
 */
const optional = (document: Record<string, unknown>, name: string): string | null =>
  document[name] === undefined || document[name] === null ? null : required(document, name);

const withoutTrailingSlash = (issuer: string): string => issuer.replace(/\/$/, "");

async function fetchEndpoints(issuer: string): Promise<OidcEndpoints> {
  const response = await fetch(`${issuer}${DISCOVERY_PATH}`, {
    headers: { accept: "application/json" },
    signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
  });
  if (!response.ok) {
    throw new Error(`Discovery answered ${response.status}.`);
  }
  const document = (await response.json()) as Record<string, unknown>;
  // OIDC Discovery 1.0 section 4.3: the document must name the issuer it was fetched for. One that
  // names another is either the wrong realm or somebody else's answer, and its tokens would carry
  // an issuer the backend does not trust.
  const advertised = document["issuer"];
  if (typeof advertised !== "string" || withoutTrailingSlash(advertised) !== issuer) {
    throw new Error("The discovery document names a different issuer.");
  }
  return {
    authorization: required(document, "authorization_endpoint"),
    token: required(document, "token_endpoint"),
    endSession: optional(document, "end_session_endpoint"),
    revocation: optional(document, "revocation_endpoint"),
  };
}

/**
 * The endpoints each issuer has advertised to this isolate. What is cached is the answer, never a
 * pending or a failed fetch. A failure is not cached at all, so an outage lasts as long as the
 * outage rather than as long as the isolate. And a promise is not shared across requests, because
 * workerd ties a fetch to the request that started it: a second request awaiting it would hang if
 * the first were cancelled. The price is that requests arriving together on a cold isolate each ask
 * once, which the provider does not notice.
 */
const discovered = new Map<string, OidcEndpoints>();

/**
 * The issuer's endpoints, from `${issuer}/.well-known/openid-configuration`, fetched once per
 * issuer per isolate. Rejects when the document cannot be fetched or does not name a usable
 * authorization and token endpoint.
 */
export async function discover(issuer: string): Promise<OidcEndpoints> {
  if (!isUsableEndpoint(issuer)) {
    throw new Error("The issuer is not an https URL, or an http URL on this machine.");
  }
  const normalized = withoutTrailingSlash(issuer);
  const cached = discovered.get(normalized);
  if (cached !== undefined) {
    return cached;
  }
  const endpoints = await fetchEndpoints(normalized);
  discovered.set(normalized, endpoints);
  return endpoints;
}

/** The issuer's endpoints, or `null` when it cannot say where they are right now. */
export const discoveredOrNull = async (env: AuthEnv): Promise<OidcEndpoints | null> => {
  try {
    return await discover(env.OIDC_ISSUER);
  } catch {
    return null;
  }
};

/**
 * 503 rather than the 502 a misconfigured deployment answers: nothing here is wrong, and an edge
 * attributes 502 to its own secrets. The provider being briefly unreachable is a different outage
 * with a different runbook, so an edge attributes this status to IDENTITY_PROVIDER_UNREACHABLE.
 * Nothing else in this module answers 503.
 */
export const IDENTITY_PROVIDER_UNREACHABLE = "identity_provider_unreachable";

/** The token endpoint refused this client: its secret, its id or its redirect URI. */
export const TOKEN_REFUSED = "token_refused";

/** The token endpoint granted a sign-in without the refresh token every session is built on. */
export const NO_REFRESH_TOKEN = "no_refresh_token";

export const identityProviderUnreachable = (retryAfter: string | null = null): Response => {
  const response = problem(
    503,
    "Service Unavailable",
    "The identity provider could not be reached.",
  );
  if (retryAfter !== null) {
    response.headers.set("Retry-After", retryAfter);
  }
  return response;
};

// ------------------------------------------------------------------------------------------------
// The login flow
// ------------------------------------------------------------------------------------------------

export const randomUrlSafe = (): string => {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
};

export const s256 = async (verifier: string): Promise<string> => {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  let binary = "";
  for (const byte of new Uint8Array(digest)) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
};

/**
 * Where the member is sent after a successful login, reduced to something that cannot leave this
 * origin. Only a path is accepted, and a path beginning with two slashes is rejected outright:
 * `//evil.example/x` is a protocol-relative URL that a browser resolves to another host entirely,
 * so admitting it would turn the callback into an open redirect that borrows axonia's name for
 * somebody else's page. A backslash, a tab or a newline is normalised by browsers into a host too,
 * so the path is resolved the way a browser would and kept only if it stays on this origin.
 * Resolving also collapses dot segments, and `/..//evil.example` collapses into `//evil.example`,
 * so a resolved path that starts with `//` is refused as well.
 * Anything failing the test becomes the app root rather than an error, because a member who
 * mistyped a link should still end up signed in.
 */
const RETURN_TO_BASE = "https://return-to.invalid";

export const safeReturnTo = (raw: string | null): string => {
  if (raw === null || !raw.startsWith("/")) return "/";
  const resolved = new URL(raw, RETURN_TO_BASE);
  if (resolved.origin !== RETURN_TO_BASE || resolved.pathname.startsWith("//")) return "/";
  return `${resolved.pathname}${resolved.search}${resolved.hash}`;
};

const isOrganizationScope = (part: string): boolean => part.startsWith("organization:");

/**
 * The same scope with any `organization:<alias>` removed. Everything else the login asked for is
 * kept, because only the organization scope is dynamic and only it can go stale.
 */
const withoutOrganization = (scope: string): string =>
  scope
    .split(" ")
    .filter((part) => part.length > 0 && !isOrganizationScope(part))
    .join(" ");

/**
 * The scope a login asks for: the configured scopes, plus any organization the link asked for. The
 * organization is the only part a link may choose, because it is the only part that is dynamic;
 * everything else is the deployment's decision, so a link can neither add `offline_access` nor
 * drop `email`. The SPA always asks for `openid profile email` plus at most one organization, so
 * against the Cloud edition's configured `openid profile email` this is exactly what it asked for.
 */
const loginScope = (env: AuthEnv, requested: string | null): string =>
  [env.OIDC_SCOPES, ...(requested ?? "").split(" ").filter(isOrganizationScope)].join(" ");

export const problem = (status: number, title: string, detail: string): Response =>
  new Response(JSON.stringify({ type: "about:blank", title, status, detail }), {
    status,
    headers: { "content-type": "application/problem+json" },
  });

export const redirect = (location: string, cookies: string[]): Response => {
  const headers = new Headers({ Location: location });
  for (const cookie of cookies) {
    headers.append("Set-Cookie", cookie);
  }
  return new Response(null, { status: 302, headers });
};

export const methodNotAllowed = (): Response =>
  problem(405, "Method Not Allowed", "That method is not accepted on this route.");

/** The two cookie names for one request, resolved once from AuthEnv rather than per handler. */
interface CookieNames {
  session: string;
  oauth: string;
}

/**
 * Marks the state of a login the callback restarted. The marker has to ride the state because the
 * state is the one thing the identity provider is bound to hand back, cookie or no cookie: a
 * callback reached without its handoff cookie can still tell a first attempt from the restart, and
 * so restart once rather than forever. A random value never contains a dot. The guard holds only
 * for a provider that echoes the state on an error redirect, as RFC 6749 requires; one that drops
 * it sends every attempt back unmarked, so the login keeps restarting.
 */
const RESTARTED_STATE_PREFIX = "r.";

/**
 * A login, started. The verifier and the state travel in a cookie rather than in the URL or in the
 * SPA's storage: the browser hands them back on the callback without any script having been able to
 * read them, which is what binds the code exchange to the browser that asked for the code.
 */
async function login(
  env: AuthEnv,
  url: URL,
  key: CryptoKey,
  cookies: CookieNames,
): Promise<Response> {
  const endpoints = await discoveredOrNull(env);
  if (endpoints === null) {
    return identityProviderUnreachable();
  }

  const verifier = randomUrlSafe();
  const restarted = url.searchParams.get("retry") === "1";
  const state = `${restarted ? RESTARTED_STATE_PREFIX : ""}${randomUrlSafe()}`;
  const scope = loginScope(env, url.searchParams.get("scope"));
  const handoff: OAuthState = {
    state,
    verifier,
    returnTo: safeReturnTo(url.searchParams.get("returnTo")),
    scope,
  };

  const authorize = new URL(endpoints.authorization);
  authorize.searchParams.set("client_id", env.OIDC_CLIENT_ID);
  authorize.searchParams.set("redirect_uri", new URL("/auth/callback", url.origin).href);
  authorize.searchParams.set("response_type", "code");
  authorize.searchParams.set("scope", scope);
  authorize.searchParams.set("state", state);
  authorize.searchParams.set("code_challenge", await s256(verifier));
  authorize.searchParams.set("code_challenge_method", "S256");
  return redirect(authorize.href, [
    cookieHeader(cookies.oauth, await seal(handoff, key), {
      maxAgeSeconds: OAUTH_MAX_AGE_SECONDS,
    }),
  ]);
}

export interface TokenResponse {
  access_token?: string;
  refresh_token?: string;
  id_token?: string;
  expires_in?: number;
}

/**
 * What the token endpoint made of a grant. `refused` is the provider's answer about the grant
 * itself, with its RFC 6749 `error` code when it sent a well-formed one; `unreachable` is a provider
 * that gave no such answer, so the grant may well still hold. `retryAfter` is the provider's own
 * Retry-After, in seconds, when it named one.
 */
type TokenOutcome =
  | { kind: "granted"; tokens: TokenResponse }
  | { kind: "refused"; error: string | undefined }
  | { kind: "unreachable"; retryAfter: string | null };

/** The refusals that are about this client, never about a member's grant. */
const CLIENT_REFUSALS: ReadonlySet<string> = new Set(["invalid_client", "unauthorized_client"]);

/**
 * A 4xx is the provider's answer about the grant, so it is a refusal. RFC 6749 refuses with 400 or
 * 401, but some providers answer a spent refresh token with another 4xx (Auth0 sends 403
 * invalid_grant), and reading any of those as an outage would keep a cookie past the refusal of the
 * grant behind it. 408 and 429 are the exceptions: the provider declining to answer right now,
 * which says nothing about the grant and asks for exactly the later attempt an outage gets.
 */
const isRefusal = (status: number): boolean =>
  status >= 400 && status < 500 && status !== 408 && status !== 429;

/**
 * RFC 6749's charset for an `error` value, capped in length. The code is all that leaves this module:
 * a provider's body can carry a description, a trace id or worse, and none of it belongs in a log.
 */
const ERROR_CODE_PATTERN = /^[\x20\x21\x23-\x5B\x5D-\x7E]{1,64}$/;

/** The `error` code a token answer names, or `undefined` when it names no well-formed one. */
const errorCodeIn = (body: unknown): string | undefined => {
  const error = isObject(body) ? body["error"] : undefined;
  return typeof error === "string" && ERROR_CODE_PATTERN.test(error) ? error : undefined;
};

const errorCodeOf = async (response: Response): Promise<string | undefined> => {
  try {
    return errorCodeIn(await response.json());
  } catch {
    return undefined;
  }
};

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/** Retry-After as a number of seconds, the one form passed on; an HTTP date is dropped. */
const retryAfterOf = (response: Response): string | null => {
  const value = response.headers.get("Retry-After");
  return value !== null && /^\d+$/.test(value) ? value : null;
};

/**
 * The one place this Worker authenticates to the token endpoint. The client secret is sent here
 * and nowhere else, and it is the reason a refresh token lifted out of a cookie is not on its own
 * enough to mint access tokens. A redirect is never followed: a 307 would replay the secret and the
 * grant to wherever its Location points, so every 3xx stays at the endpoint discovery named.
 *
 * A network error, a timeout, a 3xx, a 5xx or a success whose body is not JSON is `unreachable`:
 * none of them is the provider saying anything about the grant. The timeout covers reading the
 * body too. A success whose JSON is not an object is no grant either, so it is a refusal, as a
 * token answer without an access token is to the caller.
 */
async function requestTokens(
  env: AuthEnv,
  endpoints: OidcEndpoints,
  form: Record<string, string>,
): Promise<TokenOutcome> {
  const body = new URLSearchParams({
    ...form,
    client_id: env.OIDC_CLIENT_ID,
    client_secret: env.OIDC_CLIENT_SECRET,
  });
  try {
    const response = await fetch(endpoints.token, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body,
      redirect: "manual",
      signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
    });
    if (isRefusal(response.status)) {
      const error = await errorCodeOf(response);
      // The one 400 that is not a refusal: an error the provider defines as retryable (Entra
      // documents it for its token endpoint). Anything unreadable stays a refusal, the side that
      // never keeps a cookie past its grant.
      return response.status === 400 && error === "temporarily_unavailable"
        ? { kind: "unreachable", retryAfter: retryAfterOf(response) }
        : { kind: "refused", error };
    }
    if (!response.ok) {
      return { kind: "unreachable", retryAfter: retryAfterOf(response) };
    }
    const tokens: unknown = await response.json();
    return isObject(tokens)
      ? { kind: "granted", tokens: tokens as TokenResponse }
      : { kind: "refused", error: undefined };
  } catch {
    return { kind: "unreachable", retryAfter: null };
  }
}

/**
 * The tokens a grant earned, or `null` for any other outcome, for a caller to whom a refusal and
 * an outage are the same answer: no token came back.
 */
export async function exchange(
  env: AuthEnv,
  endpoints: OidcEndpoints,
  form: Record<string, string>,
): Promise<TokenResponse | null> {
  const outcome = await requestTokens(env, endpoints, form);
  return outcome.kind === "granted" ? outcome.tokens : null;
}

/**
 * Ends the session at the identity provider, not merely this browser's view of it, the way
 * OIDC_SESSION_END says to. `refresh-token-post` is Keycloak's own: its end-session endpoint takes
 * the refresh token rather than an access token, and ends the SSO session. `revoke` is RFC 7009,
 * which revokes the refresh token and leaves the provider's own session to the browser's redirect.
 * Both authenticate as the client, because the client is confidential; a provider advertising no
 * endpoint for the configured mode is simply not told.
 */
async function endSession(
  env: AuthEnv,
  endpoints: OidcEndpoints,
  refreshToken: string,
): Promise<void> {
  const revoking = env.OIDC_SESSION_END === "revoke";
  const target = revoking ? endpoints.revocation : endpoints.endSession;
  if (target === null) {
    return;
  }
  const body = revoking
    ? new URLSearchParams({
        token: refreshToken,
        token_type_hint: "refresh_token",
        client_id: env.OIDC_CLIENT_ID,
        client_secret: env.OIDC_CLIENT_SECRET,
      })
    : new URLSearchParams({
        client_id: env.OIDC_CLIENT_ID,
        client_secret: env.OIDC_CLIENT_SECRET,
        refresh_token: refreshToken,
      });
  try {
    await fetch(target, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body,
      redirect: "manual",
      signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
    });
  } catch {
    // A sign-out is not worth failing because the identity provider was briefly unreachable.
  }
}

/**
 * The return from the identity provider. A failure here restarts the login once, marked in the
 * state the provider hands back, rather than showing an error: the overwhelmingly common cause is
 * a handoff cookie that expired while the login form sat open, and the right answer to that is a
 * fresh authorize request, not a dead end. The marker is what stops a browser refusing cookies
 * altogether from spinning between the two.
 */
async function callback(
  request: Request,
  env: AuthEnv,
  url: URL,
  key: CryptoKey,
  cookies: CookieNames,
  sessionMaxAgeSeconds: number,
): Promise<Response> {
  const restarted = url.searchParams.get("state")?.startsWith(RESTARTED_STATE_PREFIX) === true;
  const restart = (): Response =>
    restarted
      ? problem(400, "Bad Request", "The sign-in could not be completed. Cookies may be blocked.")
      : redirect("/auth/login?retry=1", [clearedCookie(cookies.oauth)]);

  // The same one-shot guard as restart(): a provider that rejects even the base scope must not be
  // able to bounce a browser between here and the authorize endpoint forever.
  const restartWithoutOrganization = (handoff: OAuthState): Response => {
    if (restarted) {
      return problem(400, "Bad Request", "The sign-in was refused: the requested scope is invalid.");
    }
    const retry = new URL("/auth/login", url.origin);
    retry.searchParams.set("retry", "1");
    retry.searchParams.set("scope", withoutOrganization(handoff.scope));
    retry.searchParams.set("returnTo", handoff.returnTo);
    return redirect(retry.pathname + retry.search, [clearedCookie(cookies.oauth)]);
  };

  // An answer from the provider that ends the login clears the handoff, as restart()'s redirect does.
  const endingTheLogin = (response: Response): Response => {
    response.headers.append("Set-Cookie", clearedCookie(cookies.oauth));
    return response;
  };

  const sealed = readCookie(request, cookies.oauth);
  if (sealed === undefined) {
    return restart();
  }
  const handoff = await unseal<OAuthState>(sealed, key);

  // Compared against the cookie the browser sent back, which is what makes this a CSRF defence and
  // not decoration: an attacker can put any `state` they like in a URL, but cannot write this
  // cookie into the victim's browser. Error answers are checked too, or a link could steer a login
  // in flight.
  if (handoff === null || handoff.state !== url.searchParams.get("state")) {
    return restart();
  }

  const error = url.searchParams.get("error");
  // Keycloak rejecting the whole authorize request with `invalid_scope` means the session asked to
  // be scoped to an organization the realm no longer has - it was renamed, or the member was
  // removed from it. Every retry would send the same dead scope, so the member could never sign in
  // again by any route. Recovering here rather than in the SPA is what makes it a recovery at all:
  // this is where the error actually arrives, and the requested scope is sitting in the cookie
  // beside it, so the organization can be dropped and the login restarted without the browser
  // needing to be told anything. A provider that never sees an organization scope never sends this.
  if (error === "invalid_scope") {
    return restartWithoutOrganization(handoff);
  }
  // The person declined, or the provider refused them: asking again would ask the same question.
  if (error === "access_denied") {
    return endingTheLogin(
      problem(400, "Bad Request", "The sign-in was refused by the identity provider."),
    );
  }
  // The authorization endpoint's own outage, which RFC 6749 names apart from a refusal.
  if (error === "temporarily_unavailable" || error === "server_error") {
    return restarted ? endingTheLogin(identityProviderUnreachable()) : restart();
  }

  const code = url.searchParams.get("code");
  if (code === null) {
    return restart();
  }

  const endpoints = await discoveredOrNull(env);
  if (endpoints === null) {
    return identityProviderUnreachable();
  }

  const outcome = await requestTokens(env, endpoints, {
    grant_type: "authorization_code",
    code,
    redirect_uri: new URL("/auth/callback", url.origin).href,
    code_verifier: handoff.verifier,
  });
  // A restart is worth one try against an outage, since a provider back by then signs the member
  // straight in; a second outage is named as one rather than blamed on cookies.
  if (outcome.kind === "unreachable" && restarted) {
    return identityProviderUnreachable();
  }
  // A restarted sign-in that brought its handoff back intact has no cookie problem left to blame.
  if (restarted && outcome.kind !== "unreachable") {
    const ending = restartedEnding(outcome);
    if (ending !== null) {
      if (ending.failure !== undefined) {
        env.reportFailure?.(ending.failure, ending.oauthError);
      }
      return endingTheLogin(problem(ending.status, ending.title, ending.detail));
    }
  }
  if (outcome.kind !== "granted" || outcome.tokens.refresh_token === undefined) {
    return restart();
  }

  const session: SessionState = { rt: outcome.tokens.refresh_token, scope: handoff.scope };
  return redirect(handoff.returnTo, [
    cookieHeader(cookies.session, await seal(session, key), {
      maxAgeSeconds: sessionMaxAgeSeconds,
    }),
    clearedCookie(cookies.oauth),
  ]);
}

interface RestartedEnding {
  status: 400 | 502;
  title: string;
  detail: string;
  /** What is reported, or `undefined` for an ending that says nothing about the edge's settings. */
  failure: string | undefined;
  oauthError: string | undefined;
}

/**
 * How a restarted sign-in's code exchange ends it, or `null` when it signs the member in.
 *
 * Anyone can reach this: `/auth/login?retry=1` hands any caller a restarted handoff, and the
 * callback then trades whatever code that caller puts in the URL. So a refusal the caller's own
 * input can produce must never read as the edge's fault. A code that is stale, forged, replayed or
 * already spent is refused as `invalid_grant`, and every other code RFC 6749 or a provider names
 * is likewise about the request rather than the client; those end on a 400 that reports nothing.
 * Only `invalid_client` and `unauthorized_client` are about this client, and a caller cannot make
 * the provider say either, since the client's id and secret are the edge's alone. A refusal with
 * no readable code, and a 200 that is no token answer (some providers send their error with one),
 * are the provider's own wording, which no code in a URL chooses; both are read as the client's.
 * A token answer without a refresh token is a provider asked for none: Entra issues one only for
 * `offline_access`.
 *
 * That is the whole guard. The handoff a caller brings is one this edge sealed for them, so it
 * cannot be told from a member's, and needs no other check: what reaches the 502 is an answer only
 * a misconfigured client gets, so each one reported is true, however it was asked for.
 */
const restartedEnding = (
  outcome: Exclude<TokenOutcome, { kind: "unreachable" }>,
): RestartedEnding | null => {
  if (outcome.kind === "granted" && outcome.tokens.refresh_token !== undefined) {
    return null;
  }
  if (outcome.kind === "granted" && outcome.tokens.access_token !== undefined) {
    return {
      status: 502,
      title: "Bad Gateway",
      detail:
        "The identity provider issued no refresh token. OIDC_SCOPES may lack offline_access.",
      failure: NO_REFRESH_TOKEN,
      oauthError: undefined,
    };
  }
  const code = outcome.kind === "refused" ? outcome.error : errorCodeIn(outcome.tokens);
  if (code !== undefined && !CLIENT_REFUSALS.has(code)) {
    return {
      status: 400,
      title: "Bad Request",
      detail: "The sign-in could not be completed. Start it again.",
      failure: undefined,
      oauthError: undefined,
    };
  }
  return {
    status: 502,
    title: "Bad Gateway",
    detail:
      "The identity provider refused to complete the sign-in. The edge's client settings may be wrong.",
    failure: TOKEN_REFUSED,
    oauthError: code,
  };
};

/**
 * An access token, minted from the sealed refresh token and returned in the body so it lives in the
 * SPA's memory and nowhere else. The rotated refresh token replaces the cookie in the same
 * response, so the browser's copy is always the newest one and two tabs racing here converge rather
 * than fighting: whichever lands second reads the cookie the first one wrote.
 *
 * No cookie at all is a 204 with no body: a signed-out visit is the ordinary answer to "am I signed
 * in", not an error, so it logs nothing in the browser. A 401 means there was a session and it no
 * longer holds: the cookie does not unseal, or the refresh token is spent, revoked, or older than the
 * provider's idle timeout. The cookie is cleared alongside it, so the SPA's next move is a fresh
 * login rather than a retry loop against a credential that will never work again. A provider
 * that cannot be discovered, or whose token endpoint cannot be reached, times out or fails, is a
 * 503 that keeps the cookie, because the refresh token is not what failed: an outage must not sign
 * anybody out. Only the provider refusing the grant ends the session. A Retry-After the provider
 * sent rides the 503, so the SPA waits as long as it was asked to.
 *
 * The scope re-requested is the one sealed at login rather than today's OIDC_SCOPES: a refresh
 * grant can only narrow, so a session established before the setting widened must keep asking for
 * what it was granted or every renewal would fail.
 *
 * There is no CSRF token here, and none is needed: this route accepts POST alone, and a SameSite=Lax
 * cookie is not sent on a cross-site POST. That pairing is the defence rather than a coincidence of
 * style - admitting GET here would let an <img> tag on any site in the world spend somebody's
 * refresh token, so the method check in handleAuth is load-bearing and has a test of its own.
 */
async function refresh(
  request: Request,
  env: AuthEnv,
  key: CryptoKey,
  cookies: CookieNames,
  sessionMaxAgeSeconds: number,
): Promise<Response> {
  const expired = (): Response =>
    new Response(JSON.stringify({ type: "about:blank", title: "Unauthorized", status: 401 }), {
      status: 401,
      headers: {
        "content-type": "application/problem+json",
        "Set-Cookie": clearedCookie(cookies.session),
      },
    });

  const sealed = readCookie(request, cookies.session);
  if (sealed === undefined) {
    return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
  }
  const session = await unseal<SessionState>(sealed, key);
  if (session === null) {
    return expired();
  }

  const endpoints = await discoveredOrNull(env);
  if (endpoints === null) {
    return identityProviderUnreachable();
  }

  const outcome = await requestTokens(env, endpoints, {
    grant_type: "refresh_token",
    refresh_token: session.rt,
    scope: session.scope,
  });
  if (outcome.kind === "unreachable") {
    return identityProviderUnreachable(outcome.retryAfter);
  }
  if (outcome.kind === "refused") {
    // Still the member's session ending, since no renewal can succeed, but no fault of theirs.
    if (outcome.error !== undefined && CLIENT_REFUSALS.has(outcome.error)) {
      env.reportFailure?.(TOKEN_REFUSED, outcome.error);
    }
    return expired();
  }
  if (outcome.tokens.access_token === undefined) {
    return expired();
  }
  const { tokens } = outcome;

  const headers = new Headers({
    "content-type": "application/json",
    // Never a shared cache's business, and never a back-button's either.
    "Cache-Control": "no-store",
  });
  // A provider returns a rotated refresh token when rotation is on (Keycloak's revokeRefreshToken)
  // and none when it is off, so this handles both without asking which: re-sealing an unchanged
  // token is harmless and re-stamps the cookie's Max-Age, which is what makes the sixty days slide.
  const rotated: SessionState = { rt: tokens.refresh_token ?? session.rt, scope: session.scope };
  headers.append(
    "Set-Cookie",
    cookieHeader(cookies.session, await seal(rotated, key), {
      maxAgeSeconds: sessionMaxAgeSeconds,
    }),
  );

  return new Response(
    JSON.stringify({
      accessToken: tokens.access_token,
      expiresIn: tokens.expires_in ?? 0,
      idToken: tokens.id_token ?? null,
    }),
    { status: 200, headers },
  );
}

/**
 * Sign-out, in two halves that must both happen. Clearing the cookie ends the session in this
 * browser; the URL returned ends it at the identity provider, which is what stops the next visit
 * being signed straight back in by an identity cookie the SPA cannot see. The SPA navigates to it
 * rather than fetching it, because ending a provider's session is a redirect flow.
 *
 * A provider that advertises no end-session endpoint, or cannot be discovered at all, gets this
 * origin as the URL instead: the cookie is cleared either way, and a sign-out must never fail
 * because the provider is unreachable.
 */
async function logout(
  request: Request,
  env: AuthEnv,
  url: URL,
  key: CryptoKey,
  cookies: CookieNames,
): Promise<Response> {
  const sealed = readCookie(request, cookies.session);
  const session = sealed === undefined ? null : await unseal<SessionState>(sealed, key);
  const endpoints = await discoveredOrNull(env);

  let endSessionUrl = url.origin;
  if (endpoints !== null && endpoints.endSession !== null) {
    const end = new URL(endpoints.endSession);
    end.searchParams.set("client_id", env.OIDC_CLIENT_ID);
    end.searchParams.set("post_logout_redirect_uri", url.origin);
    endSessionUrl = end.href;
  }

  // Best effort, and awaited so the answer is not sent before the provider has been told. This is
  // the half that matters: clearing the cookie ends the session in THIS browser, while this ends it
  // at the provider, so a second browser holding the same identity cookie does not remain signed
  // in. A failure is swallowed rather than failing the sign-out, because the cookie is cleared
  // either way and this browser is done regardless.
  if (session !== null && endpoints !== null) {
    await endSession(env, endpoints, session.rt);
  }

  return new Response(JSON.stringify({ endSessionUrl }), {
    status: 200,
    headers: {
      "content-type": "application/json",
      "Cache-Control": "no-store",
      "Set-Cookie": clearedCookie(cookies.session),
    },
  });
}

export const isAuthPath = (pathname: string): boolean =>
  pathname === AUTH_PATH || pathname.startsWith(`${AUTH_PATH}/`);

/**
 * `COOKIE_NAME_PREFIX` must be non-empty and hold only lowercase letters, digits and hyphens. This
 * is not a security boundary - `Headers.append` already refuses CR/LF outright, a stray `;` would
 * make the browser discard the whole `Set-Cookie` rather than let anything be injected through it,
 * and `__Host-` itself refuses any cookie that carries a `Domain`, so nothing here can be turned
 * into a forged session. The danger this guards against is a broken deployment, not an exploit: a
 * whitespace-only value is truthy and would otherwise reach `sessionCookieName` unchecked, and the
 * `;` case in particular is silent total sign-in breakage - every response would set zero cookies,
 * the Worker would keep answering with healthy-looking 302s, and no gate would see it. That is the
 * exact class of quiet misconfiguration `SESSION_MAX_AGE_SECONDS` is validated against below, and
 * leaving this field merely truthy-checked while that one is strict is an asymmetry nobody should
 * have to rediscover.
 */
const COOKIE_NAME_PREFIX_PATTERN = /^[a-z0-9-]+$/;

/**
 * `OIDC_ISSUER` must be an absolute `https:` URL, or `http:` on this machine, because that is the
 * only shape discovery can safely append `/.well-known/openid-configuration` to. Anything else is a
 * deployment that cannot sign anybody in, and it has to be caught here rather than left to fail on
 * first use: an empty or relative value would reach `fetch` inside discovery and fail as though the
 * provider were down, which sends whoever is paged to the provider rather than to the Worker's own
 * configuration - no `auth_not_configured`, no runbook. That is precisely the outcome the other
 * validations exist to prevent. A cleartext issuer off this machine is refused for the reason
 * isUsableEndpoint gives: whoever sits on the path would choose where the client secret goes.
 *
 * "Malformed" is deliberately no more than that. It does not require `/realms/<name>`, allows http
 * on localhost (local development runs against an issuer on this machine), and does not check that
 * the host resolves or the realm exists. A stricter shape check would refuse a provider deployed at
 * a path prefix and would still not catch the mistake that actually happens, which is pointing at
 * the wrong realm - a value only a live discovery call can tell apart, and a deployment's own
 * sign-in check is where that belongs. Discovery itself then requires the document to name this
 * same issuer, and holds every endpoint it returns to the same https-or-localhost rule.
 */
const isUsableIssuer = (raw: string): boolean => isUsableEndpoint(raw);

/** What a request may use once the configuration has been found sound. */
export interface AuthConfiguration {
  key: CryptoKey;
  sessionMaxAgeSeconds: number;
}

/**
 * The deployment's configuration, validated, or the 502 that says it cannot sign anybody in. A
 * missing secret is a 502 rather than a 401 on purpose: it is a deployment that cannot sign anybody
 * in, not a caller who failed to authenticate, and the two deserve different pages and different
 * runbooks. `OIDC_ISSUER`, `SESSION_MAX_AGE_SECONDS`, `COOKIE_NAME_PREFIX`, `OIDC_SCOPES` and
 * `OIDC_SESSION_END` get the same treatment for the same reason: a Worker that cannot resolve its
 * own identity provider, its own session lifetime, a well-formed cookie name, the scopes it asks
 * for or how it ends a session cannot safely sign anybody in either, and the alternative - falling
 * back to a default, or trusting an unchecked value - is precisely the silent misconfiguration
 * these fields exist to rule out. Seven fields are validated here, and every edge says so when it
 * attributes this 502 to `auth_not_configured`.
 */
export async function authConfiguration(env: AuthEnv): Promise<AuthConfiguration | Response> {
  if (
    !env.SESSION_COOKIE_KEY ||
    !env.OIDC_CLIENT_SECRET ||
    !COOKIE_NAME_PREFIX_PATTERN.test(env.COOKIE_NAME_PREFIX)
  ) {
    return problem(502, "Bad Gateway", "The edge is not configured for authentication.");
  }

  if (!isUsableIssuer(env.OIDC_ISSUER)) {
    return problem(
      502,
      "Bad Gateway",
      "OIDC_ISSUER must be configured as an absolute https URL, or http on localhost.",
    );
  }

  const sessionMaxAgeSeconds = parsedSessionMaxAgeSeconds(env.SESSION_MAX_AGE_SECONDS);
  if (sessionMaxAgeSeconds === null) {
    return problem(
      502,
      "Bad Gateway",
      "SESSION_MAX_AGE_SECONDS must be configured as a positive whole number of seconds.",
    );
  }

  if (!isUsableScopes(env.OIDC_SCOPES)) {
    return problem(
      502,
      "Bad Gateway",
      "OIDC_SCOPES must be configured as space-separated scopes that include openid.",
    );
  }

  if (!SESSION_END_MODES.has(env.OIDC_SESSION_END)) {
    return problem(
      502,
      "Bad Gateway",
      "OIDC_SESSION_END must be configured as refresh-token-post or revoke.",
    );
  }

  try {
    return { key: await sealingKey(env.SESSION_COOKIE_KEY), sessionMaxAgeSeconds };
  } catch {
    return problem(502, "Bad Gateway", "The edge session key is malformed.");
  }
}

/** Every `/auth/*` request, answered. */
export async function handleAuth(request: Request, env: AuthEnv, url: URL): Promise<Response> {
  const configuration = await authConfiguration(env);
  if (configuration instanceof Response) {
    return configuration;
  }
  const { key, sessionMaxAgeSeconds } = configuration;

  const cookies: CookieNames = {
    session: sessionCookieName(env.COOKIE_NAME_PREFIX),
    oauth: oauthCookieName(env.COOKIE_NAME_PREFIX),
  };

  switch (url.pathname) {
    case `${AUTH_PATH}/login`:
      return request.method === "GET" ? login(env, url, key, cookies) : methodNotAllowed();
    case `${AUTH_PATH}/callback`:
      return request.method === "GET"
        ? callback(request, env, url, key, cookies, sessionMaxAgeSeconds)
        : methodNotAllowed();
    case `${AUTH_PATH}/refresh`:
      return request.method === "POST"
        ? refresh(request, env, key, cookies, sessionMaxAgeSeconds)
        : methodNotAllowed();
    case `${AUTH_PATH}/logout`:
      return request.method === "POST"
        ? logout(request, env, url, key, cookies)
        : methodNotAllowed();
    default:
      return problem(404, "Not Found", "No such authentication route.");
  }
}
