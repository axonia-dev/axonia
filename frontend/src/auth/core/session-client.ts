// The browser's whole side of the OAuth flow, which is now almost nothing: it asks the edge for an
// access token and navigates when it is told to. There is no client id here, no PKCE, no token
// endpoint and no stored credential, because edge/src/auth.ts holds all of it. Every application
// signing in through an edge runs this module, each behind its own.

import { decodeJwtPayload } from "./jwt";

/** What a signed-in session looks like to the app, shaped to match what the OIDC client exposed. */
export interface AuthUser {
  access_token: string;
  profile: {
    sub: string;
    email?: string;
    preferred_username?: string;
  };
}

interface RefreshResponse {
  accessToken: string;
  expiresIn: number;
  idToken: string | null;
}

/**
 * The profile, read out of the id token when Keycloak sent one and out of the access token
 * otherwise. Both carry `sub`, which is the only field anything here treats as required; a session
 * whose token has no subject is not a session, so it is reported as no session at all rather than
 * as a signed-in person with an empty identity.
 */
function profileOf(response: RefreshResponse): AuthUser["profile"] | null {
  const claims = decodeJwtPayload(response.idToken ?? response.accessToken);
  const sub = claims?.["sub"];
  if (typeof sub !== "string") return null;
  const email = claims?.["email"];
  const username = claims?.["preferred_username"];
  return {
    sub,
    ...(typeof email === "string" ? { email } : {}),
    ...(typeof username === "string" ? { preferred_username: username } : {}),
  };
}

export interface RenewedSession {
  user: AuthUser;
  /** Seconds this access token is good for, which is what the renewal timer is set from. */
  expiresIn: number;
}

/** A renewal the edge could not make now, and how long it asked to be left alone, if it said. */
export interface RenewalUnavailable {
  unavailable: true;
  retryAfterSeconds: number | null;
}

const NO_SESSION = 204;
const SERVER_ERROR = 500;

/** Retry-After as a number of seconds, the only form the edge sends. */
function retryAfterOf(response: Response): number | null {
  const value = response.headers.get("Retry-After");
  return value !== null && /^\d+$/.test(value) ? Number(value) : null;
}

/** The edge itself out of reach, as on a laptop waking before its network does. */
async function refreshRequest(): Promise<Response | null> {
  try {
    return await fetch("/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    });
  } catch {
    return null;
  }
}

/**
 * Spends the sealed cookie for a fresh access token.
 *
 * `null` means there is no session to renew. A 204 with no body is the edge saying there never was a
 * cookie, which is an ordinary signed-out visit rather than an error, and must not reach `json()`. A
 * 401 means the refresh token behind the cookie is spent, revoked, or older than the realm's idle
 * timeout; the edge has already cleared that cookie. Either way the only correct response is to
 * start a login.
 *
 * `unavailable` means the session could not be renewed right now, which is not a session that
 * ended. The edge answers 503 when the identity provider cannot be reached and keeps the cookie,
 * and neither a 5xx nor a request that never reached the edge is the edge refusing the cookie, so
 * the token in hand is kept and renewal asked again later, no sooner than any Retry-After the edge
 * passed on.
 *
 * `credentials: "same-origin"` is stated rather than left to the default because the whole
 * mechanism is the cookie riding along; a future change to the default would otherwise sign
 * everybody out with nothing in the diff to explain it.
 */
export async function renewSession(): Promise<
  RenewedSession | RenewalUnavailable | null
> {
  const response = await refreshRequest();
  if (response === null) {
    return { unavailable: true, retryAfterSeconds: null };
  }
  if (response.status >= SERVER_ERROR) {
    return { unavailable: true, retryAfterSeconds: retryAfterOf(response) };
  }
  if (response.status === NO_SESSION || !response.ok) return null;

  const body = (await response.json()) as RefreshResponse;
  const profile = profileOf(body);
  if (profile === null) return null;
  return {
    user: { access_token: body.accessToken, profile },
    expiresIn: body.expiresIn,
  };
}

/**
 * Renewal, run once per browser rather than once per tab.
 *
 * Every open tab holds its own in-memory access token and so every open tab wants to renew, but all
 * of them share one cookie and one refresh token behind it. With rotation on, two tabs spending the
 * same refresh token in the same instant is indistinguishable from a stolen token being replayed,
 * and the realm's answer to that is to end the session. The lock makes the second tab wait; by the
 * time it runs, the cookie holds the rotated token, so it renews from the newest one instead of
 * re-spending the old.
 *
 * Web Locks is absent in no browser this product supports, but it is absent in jsdom, so a missing
 * implementation falls through to an unlocked call rather than failing: one tab racing with itself
 * is not a scenario, and a test environment must not be the reason sign-in stops working.
 *
 * `lockName` is each app's own. A lock is scoped to its origin, so two apps could not collide even
 * on one name, but naming it after the app keeps the two from reading as one shared thing.
 */
export function renewOnce(
  lockName: string,
): Promise<RenewedSession | RenewalUnavailable | null> {
  const locks = navigator.locks as LockManager | undefined;
  if (locks === undefined) return renewSession();
  return locks.request(lockName, renewSession);
}

/**
 * Where the person is, in full: the query string and fragment hold URL state (a reading, a
 * selection) that a login must bring back. The edge still decides whether it is a safe path.
 */
function currentLocation(): string {
  const { pathname, search, hash } = window.location;
  return `${pathname}${search}${hash}`;
}

/**
 * Starts a login by leaving the page. This is a navigation rather than a fetch because the person
 * has to be able to see and use Keycloak's login form, and because the handoff cookie the edge sets
 * on the way out is what binds the eventual code exchange to this browser.
 */
export function startLogin(
  options: { returnTo?: string; scope?: string } = {},
): void {
  const url = new URL("/auth/login", window.location.origin);
  url.searchParams.set("returnTo", options.returnTo ?? currentLocation());
  if (options.scope !== undefined) url.searchParams.set("scope", options.scope);
  window.location.assign(url.href);
}

/**
 * Ends the session in both places it exists. The edge clears the cookie and tells Keycloak to drop
 * the SSO session; the URL it returns is then navigated to, which is what stops the next visit
 * being signed straight back in by an identity cookie this code cannot see.
 *
 * A failure still leaves the page: the cookie is cleared by the same response that failed to
 * produce a URL, so staying put would leave a person looking at an app they are no longer signed in
 * to and telling them the sign-out did not work.
 */
export async function endSession(): Promise<void> {
  try {
    const response = await fetch("/auth/logout", {
      method: "POST",
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    });
    const { endSessionUrl } = (await response.json()) as {
      endSessionUrl?: string;
    };
    window.location.assign(endSessionUrl ?? window.location.origin);
  } catch {
    window.location.assign(window.location.origin);
  }
}
