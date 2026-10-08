import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from "vitest";
import type * as AuthModule from "./auth";
import type { AuthEnv } from "./auth";
import {
  cookieNamed,
  type DiscoveryDocument,
  ENTRA_ISSUER,
  ENTRA_LIKE,
  type Fetch,
  type IdentityProviderStub,
  keycloakLike,
  setCookies,
  stubIdentityProvider,
  tokenResponse,
  valueOf,
} from "./oidc-test-support";
import {
  clearedCookie,
  cookieHeader,
  oauthCookieName,
  readCookie,
  seal,
  sealingKey,
  sessionCookieName,
  unseal,
} from "./sealed-cookie";

const KEY = "A".repeat(43);
const OTHER_KEY = "B".repeat(43);

const SESSION_MAX_AGE_SECONDS = 60 * 24 * 60 * 60;

const env: AuthEnv = {
  OIDC_ISSUER: "https://login.example.com/realms/example",
  OIDC_CLIENT_ID: "web-app",
  OIDC_CLIENT_SECRET: "a-client-secret",
  SESSION_COOKIE_KEY: KEY,
  SESSION_MAX_AGE_SECONDS: String(SESSION_MAX_AGE_SECONDS),
  COOKIE_NAME_PREFIX: "axonia",
  OIDC_SCOPES: "openid profile email",
  OIDC_SESSION_END: "refresh-token-post",
};

const KEYCLOAK_LIKE = keycloakLike(env.OIDC_ISSUER);

const SESSION_COOKIE = sessionCookieName(env.COOKIE_NAME_PREFIX);
const OAUTH_COOKIE = oauthCookieName(env.COOKIE_NAME_PREFIX);

const REFRESH_TOKEN = "the-sixty-day-refresh-token";
const ACCESS_TOKEN = "a-short-lived-access-token";

const OFF_ORIGIN_RETURN_TOS = [
  "/\\evil.example/x",
  "/\\/evil.example/x",
  "/\t/evil.example/x",
  "/\n/evil.example/x",
  "\\\\evil.example/x",
  "/..//evil.example/x",
  "/.//evil.example/x",
  "/me/../..//evil.example/x",
  "/%2e%2e//evil.example/x",
  "https://evil.example/x",
  "javascript:alert(1)",
  "evil.example",
];

let handleAuth: typeof AuthModule.handleAuth;

const call = (url: string, init?: RequestInit, customEnv: AuthEnv = env): Promise<Response> => {
  const request = new Request(url, init);
  return handleAuth(request, customEnv, new URL(url));
};

/** Every call other than discovery, which is every call the identity provider is asked to act on. */
let fetchSpy: Mock<Fetch>;
let identityProvider: IdentityProviderStub;

const fetchCalls = (): { url: string; method: string | undefined; body: URLSearchParams }[] =>
  fetchSpy.mock.calls.map(([target, init]) => ({
    url: String(target),
    method: init?.method,
    body: new URLSearchParams(String(init?.body)),
  }));

beforeEach(async () => {
  fetchSpy = vi.fn<Fetch>(async () =>
    tokenResponse({
      access_token: ACCESS_TOKEN,
      refresh_token: REFRESH_TOKEN,
      id_token: "an-id-token",
      expires_in: 300,
    }),
  );
  identityProvider = stubIdentityProvider(fetchSpy);
  identityProvider.serve(env.OIDC_ISSUER, KEYCLOAK_LIKE);
  // A fresh module is a fresh isolate. Discovery is cached in module scope, so without this the
  // first test to sign in would decide which identity provider every later test talks to.
  vi.resetModules();
  ({ handleAuth } = await import("./auth"));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("sealing", () => {
  it("opens what it sealed", async () => {
    const key = await sealingKey(KEY);

    const sealed = await seal({ rt: REFRESH_TOKEN, scope: "openid" }, key);

    expect(await unseal(sealed, key)).toEqual({ rt: REFRESH_TOKEN, scope: "openid" });
  });

  it("never leaves the secret readable in what it produces", async () => {
    const key = await sealingKey(KEY);

    const sealed = await seal({ rt: REFRESH_TOKEN }, key);

    expect(sealed).not.toContain(REFRESH_TOKEN);
  });

  it("produces a different ciphertext every time, so two seals of one session cannot be matched", async () => {
    const key = await sealingKey(KEY);

    const first = await seal({ rt: REFRESH_TOKEN }, key);
    const second = await seal({ rt: REFRESH_TOKEN }, key);

    expect(first).not.toEqual(second);
  });

  it("refuses a value edited by a byte rather than opening it into something else", async () => {
    const key = await sealingKey(KEY);
    const sealed = await seal({ rt: REFRESH_TOKEN }, key);

    // Edited in the middle rather than at the end. base64 drops the unused trailing bits of the
    // final character, so flipping the last one can decode to the very same bytes and prove
    // nothing; a character in the body always changes what the cipher is asked to authenticate.
    const midpoint = Math.floor(sealed.length / 2);
    const replacement = sealed[midpoint] === "A" ? "B" : "A";
    const tampered = sealed.slice(0, midpoint) + replacement + sealed.slice(midpoint + 1);

    expect(await unseal(tampered, key)).toBeNull();
  });

  it("refuses a value sealed under a key that has since been rotated", async () => {
    const sealed = await seal({ rt: REFRESH_TOKEN }, await sealingKey(KEY));

    expect(await unseal(sealed, await sealingKey(OTHER_KEY))).toBeNull();
  });

  it("refuses anything that is not a sealed value at all", async () => {
    const key = await sealingKey(KEY);

    expect(await unseal("not-base64-at-all!!", key)).toBeNull();
    expect(await unseal("", key)).toBeNull();
  });

  it("refuses a key that is not 32 bytes, because a short key is a weak cipher and not a warning", async () => {
    await expect(sealingKey("too-short")).rejects.toThrow(/32 bytes/);
  });
});

describe("cookie handling", () => {
  it("reads one cookie out of a header holding several", () => {
    const request = new Request("https://app.example.com/", {
      headers: { Cookie: `other=1; ${SESSION_COOKIE}=sealed-value; another=2` },
    });

    expect(readCookie(request, SESSION_COOKIE)).toBe("sealed-value");
  });

  it("answers undefined rather than a wrong value when the cookie is absent", () => {
    const request = new Request("https://app.example.com/", { headers: { Cookie: "other=1" } });

    expect(readCookie(request, SESSION_COOKIE)).toBeUndefined();
  });

  it("answers undefined when there is no Cookie header at all", () => {
    expect(readCookie(new Request("https://app.example.com/"), SESSION_COOKIE)).toBeUndefined();
  });

  it("writes a cookie no script can read and no other origin can send", () => {
    const cookie = cookieHeader(SESSION_COOKIE, "sealed", { maxAgeSeconds: 60 });

    expect(cookie).toContain("HttpOnly");
    expect(cookie).toContain("Secure");
    expect(cookie).toContain("Path=/");
    expect(cookie).toContain("SameSite=Lax");
  });

  it("clears with the same Path it was written with, or the browser keeps the original", () => {
    const cleared = clearedCookie(SESSION_COOKIE);

    expect(cleared).toContain("Max-Age=0");
    expect(cleared).toContain("Path=/");
  });
});

describe("GET /auth/login", () => {
  it("sends the member to Keycloak with PKCE and a state it can prove it issued", async () => {
    const response = await call("https://app.example.com/auth/login");

    expect(response.status).toBe(302);
    const authorize = new URL(response.headers.get("Location") ?? "");
    expect(authorize.origin + authorize.pathname).toBe(
      "https://login.example.com/realms/example/protocol/openid-connect/auth",
    );
    expect(authorize.searchParams.get("response_type")).toBe("code");
    expect(authorize.searchParams.get("client_id")).toBe("web-app");
    expect(authorize.searchParams.get("code_challenge_method")).toBe("S256");
    expect(authorize.searchParams.get("code_challenge")).toBeTruthy();
    expect(authorize.searchParams.get("state")).toBeTruthy();
    expect(authorize.searchParams.get("redirect_uri")).toBe("https://app.example.com/auth/callback");
  });

  it("keeps the verifier in a cookie rather than the URL, so no script and no referrer carries it", async () => {
    const response = await call("https://app.example.com/auth/login");

    const authorize = new URL(response.headers.get("Location") ?? "");
    expect(authorize.searchParams.get("code_verifier")).toBeNull();
    const handoff = cookieNamed(response, OAUTH_COOKIE);
    expect(handoff).toBeDefined();
    expect(handoff).toContain("HttpOnly");
  });

  it("carries the requested organization, which is how an organization-scoped session is established", async () => {
    const response = await call(
      "https://app.example.com/auth/login?scope=openid+profile+email+organization%3Aacme",
    );

    const authorize = new URL(response.headers.get("Location") ?? "");
    expect(authorize.searchParams.get("scope")).toBe("openid profile email organization:acme");
  });

  it("asks for the configured scopes when the link names none", async () => {
    const response = await call("https://app.example.com/auth/login");

    const authorize = new URL(response.headers.get("Location") ?? "");
    expect(authorize.searchParams.get("scope")).toBe("openid profile email");
  });

  // A login link is a URL anybody can hand a member. Only the organization part is dynamic; every
  // other scope is the deployment's decision, so a link cannot add offline_access or drop email.
  it("never lets a link widen or narrow the configured scopes", async () => {
    const response = await call("https://app.example.com/auth/login?scope=openid+offline_access");

    const authorize = new URL(response.headers.get("Location") ?? "");
    expect(authorize.searchParams.get("scope")).toBe("openid profile email");
  });

  it("refuses to remember a returnTo that would leave this origin", async () => {
    const response = await call("https://app.example.com/auth/login?returnTo=%2F%2Fevil.example%2Fx");

    const handoff = cookieNamed(response, OAUTH_COOKIE) ?? "";
    const opened = await unseal<{ returnTo: string }>(valueOf(handoff), await sealingKey(KEY));
    expect(opened?.returnTo).toBe("/");
  });

  it("remembers a returnTo that is a path on this origin", async () => {
    const response = await call("https://app.example.com/auth/login?returnTo=%2Fme%2Fprofile");

    const handoff = cookieNamed(response, OAUTH_COOKIE) ?? "";
    const opened = await unseal<{ returnTo: string }>(valueOf(handoff), await sealingKey(KEY));
    expect(opened?.returnTo).toBe("/me/profile");
  });

  it.each(OFF_ORIGIN_RETURN_TOS)("refuses %j as a returnTo", async (raw) => {
    const response = await call(
      `https://app.example.com/auth/login?returnTo=${encodeURIComponent(raw)}`,
    );
    const handoff = cookieNamed(response, OAUTH_COOKIE) ?? "";
    const opened = await unseal<{ returnTo: string }>(valueOf(handoff), await sealingKey(KEY));
    expect(opened?.returnTo).toBe("/");
  });

  it("remembers the consent page with its whole query string", async () => {
    const consent =
      "/oauth/authorize?response_type=code&client_id=https%3A%2F%2Fclaude.ai%2Foauth%2Fx&state=a%26b%3Dc&scope=read+topic%3Apropose";
    const response = await call(
      `https://app.example.com/auth/login?returnTo=${encodeURIComponent(consent)}`,
    );
    const handoff = cookieNamed(response, OAUTH_COOKIE) ?? "";
    const opened = await unseal<{ returnTo: string }>(valueOf(handoff), await sealingKey(KEY));
    expect(opened?.returnTo).toBe(consent);
  });

  it("is a GET and nothing else", async () => {
    expect((await call("https://app.example.com/auth/login", { method: "POST" })).status).toBe(405);
  });
});

/** Drives a real /auth/login and hands back what the browser would send to the callback. */
async function startedLogin(
  query = "",
  customEnv: AuthEnv = env,
): Promise<{ cookie: string; state: string }> {
  const response = await call(`https://app.example.com/auth/login${query}`, undefined, customEnv);
  const oauthCookie = oauthCookieName(customEnv.COOKIE_NAME_PREFIX);
  const handoff = cookieNamed(response, oauthCookie) ?? "";
  const state = new URL(response.headers.get("Location") ?? "").searchParams.get("state") ?? "";
  return { cookie: `${oauthCookie}=${valueOf(handoff)}`, state };
}

/** The provider's return to the callback with a code, carrying `cookie` when it is not empty. */
const callback = (state: string, cookie: string, customEnv: AuthEnv = env): Promise<Response> =>
  call(
    `https://app.example.com/auth/callback?code=the-code&state=${state}`,
    cookie === "" ? undefined : { headers: { Cookie: cookie } },
    customEnv,
  );

describe("GET /auth/callback", () => {
  it.each(OFF_ORIGIN_RETURN_TOS)("keeps the browser on this origin for a returnTo of %j", async (raw) => {
    const { cookie, state } = await startedLogin(`?returnTo=${encodeURIComponent(raw)}`);

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    const location = response.headers.get("Location") ?? "";
    expect(new URL(location, "https://app.example.com").origin).toBe("https://app.example.com");
  });

  it("exchanges the code with the client secret and seals the refresh token into a cookie", async () => {
    const { cookie, state } = await startedLogin();

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(response.status).toBe(302);
    const body = new URLSearchParams(String(fetchSpy.mock.calls[0]?.[1]?.body));
    expect(body.get("grant_type")).toBe("authorization_code");
    expect(body.get("code")).toBe("the-code");
    expect(body.get("client_secret")).toBe("a-client-secret");
    expect(body.get("code_verifier")).toBeTruthy();
  });

  it("hands the browser a sealed cookie, never the refresh token itself", async () => {
    const { cookie, state } = await startedLogin();

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    const session = cookieNamed(response, SESSION_COOKIE) ?? "";
    expect(session).not.toContain(REFRESH_TOKEN);
    expect(session).toContain("HttpOnly");
    expect(await response.text()).toBe("");
    const opened = await unseal<{ rt: string }>(valueOf(session), await sealingKey(KEY));
    expect(opened?.rt).toBe(REFRESH_TOKEN);
  });

  it("returns the member to where the login started", async () => {
    const { cookie, state } = await startedLogin("?returnTo=%2Fme%2Fprofile");

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(response.headers.get("Location")).toBe("/me/profile");
  });

  it("clears the handoff cookie, so a verifier cannot be paired with a second code", async () => {
    const { cookie, state } = await startedLogin();

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(cookieNamed(response, OAUTH_COOKIE)).toContain("Max-Age=0");
  });

  it("refuses a state the browser was never issued, which is what makes this CSRF-resistant", async () => {
    const { cookie } = await startedLogin();

    const response = await call(
      "https://app.example.com/auth/callback?code=the-code&state=a-state-an-attacker-chose",
      { headers: { Cookie: cookie } },
    );

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("restarts a login whose handoff cookie expired while the form sat open", async () => {
    const response = await call("https://app.example.com/auth/callback?code=the-code&state=whatever");

    expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
  });

  it("stops rather than looping when the restart itself comes back empty-handed", async () => {
    const { state } = await startedLogin("?retry=1");

    // The provider sends the browser back with the state it was given, and no cookie came along.
    const response = await call(`https://app.example.com/auth/callback?code=the-code&state=${state}`);

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ detail: expect.stringContaining("Cookies") });
  });

  it("marks only a restarted login, so a first one is still restarted once", async () => {
    const first = await startedLogin();
    const restarted = await startedLogin("?retry=1");

    expect(first.state).not.toMatch(/^r\./);
    expect(restarted.state).toMatch(/^r\./);
  });

  it("restarts rather than signing anybody in when Keycloak refuses the code", async () => {
    const { cookie, state } = await startedLogin();
    fetchSpy.mockResolvedValue(tokenResponse({ error: "invalid_grant" }, 400));

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(cookieNamed(response, SESSION_COOKIE)).toBeUndefined();
    expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
  });
});

describe("a session scoped to an organization that no longer exists", () => {
  it("drops the organization and signs the member in without it, rather than locking them out", async () => {
    const { cookie, state } = await startedLogin(
      "?scope=openid+profile+email+organization%3Aacme&returnTo=%2Fme",
    );

    const response = await call(
      `https://app.example.com/auth/callback?error=invalid_scope&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(response.status).toBe(302);
    const retry = new URL(response.headers.get("Location") ?? "", "https://app.example.com");
    expect(retry.pathname).toBe("/auth/login");
    expect(retry.searchParams.get("scope")).toBe("openid profile email");
    expect(retry.searchParams.get("returnTo")).toBe("/me");
  });

  it("keeps every configured scope", async () => {
    const withOfflineAccess: AuthEnv = { ...env, OIDC_SCOPES: "openid email offline_access" };
    const { cookie, state } = await startedLogin(
      "?scope=openid+email+organization%3Aacme",
      withOfflineAccess,
    );

    const response = await call(
      `https://app.example.com/auth/callback?error=invalid_scope&state=${state}`,
      { headers: { Cookie: cookie } },
      withOfflineAccess,
    );

    const retry = new URL(response.headers.get("Location") ?? "", "https://app.example.com");
    expect(retry.searchParams.get("scope")).toBe("openid email offline_access");
  });

  it("gives up rather than bouncing when even the base scope is refused", async () => {
    const { cookie, state } = await startedLogin("?scope=openid+organization%3Aacme&retry=1");

    const response = await call(
      `https://app.example.com/auth/callback?error=invalid_scope&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(response.status).toBe(400);
  });
});

describe("an authorization the identity provider could not give", () => {
  const authorization = async (error: string, login = ""): Promise<Response> => {
    const { cookie, state } = await startedLogin(login);
    return call(`https://app.example.com/auth/callback?error=${error}&state=${state}`, {
      headers: { Cookie: cookie },
    });
  };

  it.each(["temporarily_unavailable", "server_error"])(
    "restarts the login once when the authorization endpoint answers %s",
    async (error) => {
      const response = await authorization(error);

      expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
    },
  );

  it.each(["temporarily_unavailable", "server_error"])(
    "answers 503 rather than blaming cookies when the restarted login meets %s",
    async (error) => {
      const response = await authorization(error, "?retry=1");

      expect(response.status).toBe(503);
      expect(await response.json()).toMatchObject({
        detail: "The identity provider could not be reached.",
      });
    },
  );

  it.each(["", "?retry=1"])("says the sign-in was refused rather than asking again (login %j)", async (login) => {
    const response = await authorization("access_denied", login);

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({
      detail: "The sign-in was refused by the identity provider.",
    });
  });

  it.each([
    ["access_denied", ""],
    ["temporarily_unavailable", "?retry=1"],
    ["server_error", "?retry=1"],
  ])("clears the handoff cookie once %s ends the login (login %j)", async (error, login) => {
    const response = await authorization(error, login);

    expect(cookieNamed(response, OAUTH_COOKIE)).toContain("Max-Age=0");
  });

  it.each(["invalid_scope", "access_denied", "temporarily_unavailable", "server_error"])(
    "restarts rather than act on %s carrying a state the browser was never issued",
    async (error) => {
      const { cookie } = await startedLogin("?scope=openid+organization%3Aacme");

      const response = await call(
        `https://app.example.com/auth/callback?error=${error}&state=a-state-an-attacker-chose`,
        { headers: { Cookie: cookie } },
      );

      expect(response.status).toBe(302);
      expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
    },
  );

  it.each(["invalid_scope", "access_denied", "temporarily_unavailable", "server_error"])(
    "blames cookies rather than act on %s carrying a restarted state the browser was never issued",
    async (error) => {
      const { cookie } = await startedLogin("?retry=1&scope=openid+organization%3Aacme");

      const response = await call(
        `https://app.example.com/auth/callback?error=${error}&state=r.a-state-an-attacker-chose`,
        { headers: { Cookie: cookie } },
      );

      expect(response.status).toBe(400);
      expect(await response.json()).toMatchObject({
        detail: "The sign-in could not be completed. Cookies may be blocked.",
      });
    },
  );
});

/** A browser holding a live session cookie. */
async function signedIn(scope = "openid profile email"): Promise<string> {
  const sealed = await seal({ rt: REFRESH_TOKEN, scope }, await sealingKey(KEY));
  return `${SESSION_COOKIE}=${sealed}`;
}

describe("POST /auth/refresh", () => {
  it("returns an access token to memory and keeps the refresh token out of the body", async () => {
    const response = await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(response.status).toBe(200);
    const body = await response.text();
    expect(JSON.parse(body)).toMatchObject({ accessToken: ACCESS_TOKEN, expiresIn: 300 });
    expect(body).not.toContain(REFRESH_TOKEN);
  });

  it("re-requests the scope the session was established with, or the org claim would vanish", async () => {
    await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn("openid organization:acme") },
    });

    const body = new URLSearchParams(String(fetchSpy.mock.calls[0]?.[1]?.body));
    expect(body.get("grant_type")).toBe("refresh_token");
    expect(body.get("scope")).toBe("openid organization:acme");
  });

  it("re-seals the rotated token, so the next renewal spends the new one", async () => {
    fetchSpy.mockResolvedValue(
      tokenResponse({ access_token: ACCESS_TOKEN, refresh_token: "a-rotated-token", expires_in: 300 }),
    );

    const response = await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    const session = cookieNamed(response, SESSION_COOKIE) ?? "";
    const opened = await unseal<{ rt: string }>(valueOf(session), await sealingKey(KEY));
    expect(opened?.rt).toBe("a-rotated-token");
  });

  it("re-stamps the lifetime on every renewal, which is what makes the sixty days slide", async () => {
    const response = await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(cookieNamed(response, SESSION_COOKIE)).toContain(`Max-Age=${SESSION_MAX_AGE_SECONDS}`);
  });

  it("is never stored by a cache, shared or otherwise", async () => {
    const response = await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(response.headers.get("Cache-Control")).toBe("no-store");
  });

  it("answers 204 with no body and no cookie when there was never a session, which is no error", async () => {
    const response = await call("https://app.example.com/auth/refresh", { method: "POST" });

    expect(response.status).toBe(204);
    expect(await response.text()).toBe("");
    expect(response.headers.getSetCookie()).toEqual([]);
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("answers 401 and clears the cookie when the refresh token is spent or revoked", async () => {
    fetchSpy.mockResolvedValue(tokenResponse({ error: "invalid_grant" }, 400));

    const response = await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(response.status).toBe(401);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
  });

  it("answers 401 for a forged cookie rather than telling the forger it was close", async () => {
    const forged = await seal({ rt: REFRESH_TOKEN }, await sealingKey(OTHER_KEY));

    const response = await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: `${SESSION_COOKIE}=${forged}` },
    });

    expect(response.status).toBe(401);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("is a POST and nothing else, so no navigation or image tag can spend a refresh token", async () => {
    expect((await call("https://app.example.com/auth/refresh")).status).toBe(405);
  });
});

describe("a renewal the identity provider could not answer", () => {
  const renewal = async (): Promise<Response> =>
    call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

  const expectKeptSession = async (response: Response): Promise<void> => {
    expect(response.status).toBe(503);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
    expect(await response.json()).toMatchObject({
      status: 503,
      detail: "The identity provider could not be reached.",
    });
    expect(response.headers.getSetCookie()).toEqual([]);
  };

  it("keeps the session when the token endpoint cannot be reached at all", async () => {
    fetchSpy.mockRejectedValue(new TypeError("fetch failed"));

    await expectKeptSession(await renewal());
  });

  it("gives up on a token endpoint that does not answer in time, and keeps the session", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    fetchSpy.mockRejectedValue(
      new DOMException("The operation was aborted due to timeout", "TimeoutError"),
    );

    const response = await renewal();

    await expectKeptSession(response);
    const tokenRequest = fetchSpy.mock.calls[0]?.[1];
    expect(timeout).toHaveBeenLastCalledWith(5000);
    expect(tokenRequest?.signal).toBe(timeout.mock.results.at(-1)?.value);
  });

  it.each([500, 502, 503, 504])("keeps the session when the token endpoint fails with %i", async (status) => {
    fetchSpy.mockResolvedValue(new Response("<html>down for maintenance</html>", { status }));

    await expectKeptSession(await renewal());
  });

  // 429 and 408 are the provider declining to answer now, which says nothing about the grant, and
  // another attempt is what both ask for.
  it.each([408, 429])("keeps the session when the token endpoint answers %i, which asks to be asked later", async (status) => {
    fetchSpy.mockResolvedValue(new Response(null, { status }));

    await expectKeptSession(await renewal());
  });

  it("keeps the session when the token endpoint answers a redirect fetch could not follow", async () => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 300 }));

    await expectKeptSession(await renewal());
  });

  it("keeps the session when a healthy status carries no token answer at all", async () => {
    fetchSpy.mockResolvedValue(new Response("<html>a captive portal</html>", { status: 200 }));

    await expectKeptSession(await renewal());
  });

  // A 400 whose error is temporarily_unavailable is the provider saying it cannot answer now, not
  // that the grant is spent.
  it("keeps the session when the token endpoint answers 400 temporarily_unavailable", async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify({ error: "temporarily_unavailable" }), {
        status: 400,
        headers: { "content-type": "application/json", "Retry-After": "30" },
      }),
    );

    const response = await renewal();

    await expectKeptSession(response);
    expect(response.headers.get("Retry-After")).toBe("30");
  });

  // A 3xx is never followed: a 307 would replay the client secret and the refresh token to wherever
  // its Location points.
  it("keeps the session rather than following a redirect away from the token endpoint", async () => {
    fetchSpy.mockResolvedValue(
      new Response(null, { status: 307, headers: { Location: "https://elsewhere.example/token" } }),
    );

    const response = await renewal();

    await expectKeptSession(response);
    expect(fetchSpy).toHaveBeenCalledOnce();
    expect(fetchSpy.mock.calls[0]?.[1]?.redirect).toBe("manual");
  });

  it("passes on how long the provider asked to be left alone", async () => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 429, headers: { "Retry-After": "120" } }));

    const response = await renewal();

    await expectKeptSession(response);
    expect(response.headers.get("Retry-After")).toBe("120");
  });

  it.each(["Wed, 21 Oct 2026 07:28:00 GMT", "120abc"])("passes on no Retry-After it cannot read as a number of seconds (%s)", async (retryAfter) => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 503, headers: { "Retry-After": retryAfter } }));

    const response = await renewal();

    await expectKeptSession(response);
    expect(response.headers.get("Retry-After")).toBeNull();
  });

  // A refusal must never be read as an outage: the cookie would outlive the grant behind it. Every
  // other 4xx is a refusal too, because some providers refuse a spent token with 403 rather than
  // the 400 RFC 6749 names. A success that is not a token answer is a refusal as well: some
  // providers send their error with a 200, and nothing in it is a grant.
  it.each([
    [400, { error: "invalid_grant" }],
    [400, { error: "invalid_scope" }],
    [401, { error: "invalid_client" }],
    [403, { error: "invalid_grant" }],
    [404, { error: "not_found" }],
    [200, { error: "invalid_grant" }],
    [200, {}],
    [200, null],
    [200, []],
    [200, "a token"],
  ])("ends the session when the token endpoint answers %i %j", async (status, body) => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } }),
    );

    const response = await renewal();

    expect(response.status).toBe(401);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
  });

  // Only a 400 whose body names temporarily_unavailable, exactly, is an outage. A body that cannot
  // be read, another error, or the same error under another status stays a refusal.
  it.each([
    [400, "", "text/plain"],
    [400, "<html>Bad Request</html>", "text/html"],
    [400, JSON.stringify({ error: "unauthorized_client" }), "application/json"],
    [400, JSON.stringify({ error: "invalid_request" }), "application/json"],
    [400, JSON.stringify({ error: "temporarily_unavailable_elsewhere" }), "application/json"],
    [401, JSON.stringify({ error: "temporarily_unavailable" }), "application/json"],
    [403, JSON.stringify({ error: "temporarily_unavailable" }), "application/json"],
  ])("ends the session when the token endpoint answers %i %s", async (status, body, type) => {
    fetchSpy.mockResolvedValue(new Response(body, { status, headers: { "content-type": type } }));

    const response = await renewal();

    expect(response.status).toBe(401);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
  });

  // A refusal of the client itself is never a member's grant ending: every session ends at its next
  // renewal, so it is reported, though the answer stays the 401 that ends the session.
  it.each([
    [401, "invalid_client"],
    [400, "invalid_client"],
    [400, "unauthorized_client"],
  ])("reports a renewal the token endpoint answers %i %s, and still ends the session", async (status, error) => {
    fetchSpy.mockResolvedValue(tokenResponse({ error }, status));
    const reportFailure = vi.fn();

    const response = await call(
      "https://app.example.com/auth/refresh",
      { method: "POST", headers: { Cookie: await signedIn() } },
      { ...env, reportFailure },
    );

    expect(response.status).toBe(401);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
    expect(reportFailure.mock.calls).toEqual([["token_refused", error]]);
  });

  it.each([
    ["refused as invalid_grant", (): Response => tokenResponse({ error: "invalid_grant" }, 400)],
    ["refused with no readable code", (): Response => new Response("<html>Unauthorized</html>", { status: 401 })],
    ["answered without an access token", (): Response => tokenResponse({ refresh_token: "rotated" })],
    ["answered 400 temporarily_unavailable", (): Response => tokenResponse({ error: "temporarily_unavailable" }, 400)],
    ["unreachable", (): Response => new Response(null, { status: 503 })],
  ])("reports nothing for a renewal %s", async (_case, answer) => {
    fetchSpy.mockResolvedValue(answer());
    const reportFailure = vi.fn();

    await call(
      "https://app.example.com/auth/refresh",
      { method: "POST", headers: { Cookie: await signedIn() } },
      { ...env, reportFailure },
    );

    expect(reportFailure).not.toHaveBeenCalled();
  });

  it("asks again on the next renewal, because nothing about the outage is remembered", async () => {
    fetchSpy.mockResolvedValueOnce(new Response(null, { status: 503 }));
    const failed = await renewal();

    const recovered = await renewal();

    expect(failed.status).toBe(503);
    expect(recovered.status).toBe(200);
  });
});

describe("a code exchange the identity provider could not answer", () => {
  it("gives up on a token endpoint that does not answer in time and restarts the login once", async () => {
    const { cookie, state } = await startedLogin();
    const timeout = vi.spyOn(AbortSignal, "timeout");
    fetchSpy.mockRejectedValue(
      new DOMException("The operation was aborted due to timeout", "TimeoutError"),
    );

    const response = await call(`https://app.example.com/auth/callback?code=the-code&state=${state}`, {
      headers: { Cookie: cookie },
    });

    expect(timeout).toHaveBeenLastCalledWith(5000);
    expect(fetchSpy.mock.calls[0]?.[1]?.signal).toBe(timeout.mock.results.at(-1)?.value);
    expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
  });

  it("answers 503 rather than blaming cookies when the restarted login meets the same outage", async () => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(new Response(null, { status: 503 }));

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
    );

    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({
      detail: "The identity provider could not be reached.",
    });
  });

  // Twice refused with the handoff intact, for a reason only the provider chooses, is no cookie
  // problem: the provider refuses this client, an expired secret or a wrong id.
  it.each([
    [401, JSON.stringify({ error: "invalid_client" }), "application/json"],
    [400, JSON.stringify({ error: "unauthorized_client" }), "application/json"],
    [400, "<html>Bad Request</html>", "text/html"],
  ])("answers 502 naming the client settings when the restarted login's code is refused with %i %s", async (status, body, type) => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(new Response(body, { status, headers: { "content-type": type } }));

    const response = await callback(state, cookie);

    expect(response.status).toBe(502);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
    expect(await response.json()).toEqual({
      type: "about:blank",
      title: "Bad Gateway",
      status: 502,
      detail:
        "The identity provider refused to complete the sign-in. The edge's client settings may be wrong.",
    });
    expect(cookieNamed(response, OAUTH_COOKIE)).toContain("Max-Age=0");
    expect(cookieNamed(response, SESSION_COOKIE)).toBeUndefined();
  });

  it("reports the provider's error code, and nothing else of its body, when the restarted login's code is refused", async () => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(
      tokenResponse(
        { error: "invalid_client", error_description: "AADSTS7000222: the client secret expired" },
        401,
      ),
    );
    const reportFailure = vi.fn();

    await callback(state, cookie, { ...env, reportFailure });

    expect(reportFailure.mock.calls).toEqual([["token_refused", "invalid_client"]]);
  });

  // A code is a short token from RFC 6749's charset. Anything else may be a sentence, a trace id or
  // a token echoed back, and none of it is passed on: it reads as no code at all.
  it.each([
    ["a body that is not JSON", "<html>Bad Request</html>"],
    ["no error at all", JSON.stringify({ error_description: "invalid_client" })],
    ["an error that is not a string", JSON.stringify({ error: 401 })],
    ["an error longer than 64 characters", JSON.stringify({ error: "x".repeat(65) })],
    ["an error with a quote in it", JSON.stringify({ error: 'invalid"client' })],
    ["an error with a backslash in it", JSON.stringify({ error: "invalid\\client" })],
    ["an error with a line break in it", JSON.stringify({ error: "invalid\nclient" })],
    ["an error with a character outside ASCII", JSON.stringify({ error: "invalid_cliënt" })],
    ["an error with a DEL in it", JSON.stringify({ error: "invalid\u007Fclient" })],
    ["an empty error", JSON.stringify({ error: "" })],
  ])("reports no error code for %s", async (_case, body) => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(new Response(body, { status: 401 }));
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.status).toBe(502);
    expect(reportFailure.mock.calls).toEqual([["token_refused", undefined]]);
  });

  // Anyone can start a restarted login and bring it back with a code of their choosing, so a code
  // the provider refuses for itself is the caller's input, never the edge's settings. A junk
  // code, five times over: before this, each was a 502 and an error line.
  it.each([
    ["a junk code, refused as invalid_grant", 400, { error: "invalid_grant" }],
    ["a malformed request", 400, { error: "invalid_request" }],
    ["a code another provider names", 400, { error: "AADSTS70008" }],
    ["the longest code it reads", 400, { error: "x".repeat(64) }],
    ["a code with a space and RFC 6749's other punctuation", 400, { error: "a b!#[]~" }],
    ["invalid_grant sent with a 200", 200, { error: "invalid_grant" }],
  ])("ends a restarted login refused for %s on a 400 that reports nothing", async (_case, status, body) => {
    const reportFailure = vi.fn();
    fetchSpy.mockImplementation(async () => tokenResponse(body, status));

    for (let attempt = 0; attempt < 5; attempt++) {
      const { cookie, state } = await startedLogin("?retry=1");
      const response = await callback(state, cookie, { ...env, reportFailure });

      expect(response.status).toBe(400);
      expect(await response.json()).toEqual({
        type: "about:blank",
        title: "Bad Request",
        status: 400,
        detail: "The sign-in could not be completed. Start it again.",
      });
      expect(cookieNamed(response, OAUTH_COOKIE)).toContain("Max-Age=0");
      expect(cookieNamed(response, SESSION_COOKIE)).toBeUndefined();
    }
    expect(fetchSpy).toHaveBeenCalledTimes(5);
    expect(reportFailure).not.toHaveBeenCalled();
  });

  it("reports unauthorized_client as it came", async () => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(tokenResponse({ error: "unauthorized_client" }, 400));
    const reportFailure = vi.fn();

    await callback(state, cookie, { ...env, reportFailure });

    expect(reportFailure.mock.calls).toEqual([["token_refused", "unauthorized_client"]]);
  });

  it("restarts a first login whose code is refused, and reports nothing yet", async () => {
    const { cookie, state } = await startedLogin();
    fetchSpy.mockResolvedValue(tokenResponse({ error: "invalid_client" }, 401));
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
    expect(reportFailure).not.toHaveBeenCalled();
  });

  it("reports nothing when the restarted login meets an outage rather than a refusal", async () => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(new Response(null, { status: 503 }));
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.status).toBe(503);
    expect(reportFailure).not.toHaveBeenCalled();
  });

  // Entra issues a refresh token only for offline_access, and a session is nothing but one.
  it("answers 502 naming offline_access when the restarted login is granted no refresh token", async () => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(tokenResponse({ access_token: ACCESS_TOKEN, expires_in: 300 }));
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.status).toBe(502);
    expect(await response.json()).toEqual({
      type: "about:blank",
      title: "Bad Gateway",
      status: 502,
      detail: "The identity provider issued no refresh token. OIDC_SCOPES may lack offline_access.",
    });
    expect(cookieNamed(response, OAUTH_COOKIE)).toContain("Max-Age=0");
    expect(cookieNamed(response, SESSION_COOKIE)).toBeUndefined();
    expect(reportFailure.mock.calls).toEqual([["no_refresh_token", undefined]]);
  });

  // Some providers send their error with a 200; with no access token in it, it is a refusal.
  it.each([
    [{ error: "invalid_client" }, "invalid_client"],
    [{}, undefined],
  ])("reads a restarted login answered 200 %j as refused", async (body, code) => {
    const { cookie, state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(tokenResponse(body));
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({
      detail:
        "The identity provider refused to complete the sign-in. The edge's client settings may be wrong.",
    });
    expect(reportFailure.mock.calls).toEqual([["token_refused", code]]);
  });

  it("restarts a first login granted no refresh token, and reports nothing yet", async () => {
    const { cookie, state } = await startedLogin();
    fetchSpy.mockResolvedValue(tokenResponse({ access_token: ACCESS_TOKEN }));
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.headers.get("Location")).toBe("/auth/login?retry=1");
    expect(reportFailure).not.toHaveBeenCalled();
  });

  it("reports nothing when the restarted login is signed in", async () => {
    const { cookie, state } = await startedLogin("?retry=1");
    const reportFailure = vi.fn();

    const response = await callback(state, cookie, { ...env, reportFailure });

    expect(response.status).toBe(302);
    expect(cookieNamed(response, SESSION_COOKIE)).toBeDefined();
    expect(reportFailure).not.toHaveBeenCalled();
  });

  // Without its handoff the edge cannot tell a refused client from a browser that drops cookies,
  // and it never asks the provider anything.
  it.each([
    ["no handoff", (): string => ""],
    ["a handoff that does not open", (): string => `${OAUTH_COOKIE}=not-a-sealed-value`],
  ])("still names cookies when the restarted login comes back with %s", async (_case, cookieOf) => {
    const { state } = await startedLogin("?retry=1");
    fetchSpy.mockResolvedValue(tokenResponse({ error: "invalid_client" }, 401));
    const reportFailure = vi.fn();

    const response = await callback(state, cookieOf(), { ...env, reportFailure });

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({
      detail: "The sign-in could not be completed. Cookies may be blocked.",
    });
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(reportFailure).not.toHaveBeenCalled();
  });

  it("never follows a redirect away from the token endpoint with the code and the client secret", async () => {
    const { cookie, state } = await startedLogin();

    await call(`https://app.example.com/auth/callback?code=the-code&state=${state}`, {
      headers: { Cookie: cookie },
    });

    expect(fetchSpy.mock.calls[0]?.[1]?.redirect).toBe("manual");
  });
});

describe("POST /auth/logout", () => {
  it("clears the cookie and ends the session at Keycloak, not only in this browser", async () => {
    const response = await call("https://app.example.com/auth/logout", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
    const [target, init] = fetchSpy.mock.calls[0] ?? [];
    expect(String(target)).toContain("/protocol/openid-connect/logout");
    const body = new URLSearchParams(String((init as RequestInit).body));
    expect(body.get("refresh_token")).toBe(REFRESH_TOKEN);
    expect(body.get("client_secret")).toBe("a-client-secret");
  });

  it("hands back the URL the SPA must navigate to, because ending an SSO session is a redirect", async () => {
    const response = await call("https://app.example.com/auth/logout", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    const { endSessionUrl } = (await response.json()) as { endSessionUrl: string };
    expect(endSessionUrl).toContain("/protocol/openid-connect/logout");
    expect(new URL(endSessionUrl).searchParams.get("post_logout_redirect_uri")).toBe(
      "https://app.example.com",
    );
  });

  it("still clears the cookie when there was no session to end", async () => {
    const response = await call("https://app.example.com/auth/logout", { method: "POST" });

    expect(response.status).toBe(200);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
  });

  it("signs the member out of this browser even when Keycloak is unreachable", async () => {
    fetchSpy.mockRejectedValue(new Error("unreachable"));

    const response = await call("https://app.example.com/auth/logout", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(response.status).toBe(200);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
  });

  it("gives up on a provider that does not answer in time rather than holding the sign-out", async () => {
    const cookie = await signedIn();
    const timeout = vi.spyOn(AbortSignal, "timeout");
    fetchSpy.mockRejectedValue(
      new DOMException("The operation was aborted due to timeout", "TimeoutError"),
    );

    const response = await call("https://app.example.com/auth/logout", {
      method: "POST",
      headers: { Cookie: cookie },
    });

    expect(fetchSpy.mock.calls[0]?.[1]?.signal).toBe(timeout.mock.results.at(-1)?.value);
    expect(timeout).toHaveBeenLastCalledWith(5000);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
  });

  it("never follows a redirect away from the end-session endpoint with the client secret", async () => {
    await call("https://app.example.com/auth/logout", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(fetchSpy.mock.calls[0]?.[1]?.redirect).toBe("manual");
  });
});

describe("session lifetime and cookie names as configuration", () => {
  it("issues the configured lifetime rather than a hard-coded one", async () => {
    const oneHour: AuthEnv = { ...env, SESSION_MAX_AGE_SECONDS: "3600" };
    const { cookie, state } = await startedLogin("", oneHour);

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
      oneHour,
    );

    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=3600");
  });

  it("keeps the configured lifetime on the re-stamp /auth/refresh does, not only on login", async () => {
    const oneHour: AuthEnv = { ...env, SESSION_MAX_AGE_SECONDS: "3600" };
    const { cookie, state } = await startedLogin("", oneHour);
    const signedIn = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
      oneHour,
    );
    const session = cookieNamed(signedIn, SESSION_COOKIE) ?? "";

    const refreshed = await call(
      "https://app.example.com/auth/refresh",
      { method: "POST", headers: { Cookie: `${SESSION_COOKIE}=${valueOf(session)}` } },
      oneHour,
    );

    expect(cookieNamed(refreshed, SESSION_COOKIE)).toContain("Max-Age=3600");
  });

  it("sets and reads back a configured cookie name, so a renewal actually resumes the session", async () => {
    const anotherApp: AuthEnv = { ...env, COOKIE_NAME_PREFIX: "another-app" };
    const anotherSessionCookie = sessionCookieName("another-app");
    const { cookie, state } = await startedLogin("", anotherApp);

    const signedIn = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
      anotherApp,
    );
    const session = cookieNamed(signedIn, anotherSessionCookie);
    expect(session).toBeDefined();
    expect(cookieNamed(signedIn, SESSION_COOKIE)).toBeUndefined();

    const refreshed = await call(
      "https://app.example.com/auth/refresh",
      { method: "POST", headers: { Cookie: `${anotherSessionCookie}=${valueOf(session ?? "")}` } },
      anotherApp,
    );

    expect(refreshed.status).toBe(200);
    expect(cookieNamed(refreshed, anotherSessionCookie)).toBeDefined();
  });

  it("keeps the browser-enforced __Host- prefix no matter what is configured", async () => {
    const anotherApp: AuthEnv = { ...env, COOKIE_NAME_PREFIX: "another-app" };

    const response = await call("https://app.example.com/auth/login", undefined, anotherApp);

    const handoff = cookieNamed(response, oauthCookieName("another-app")) ?? "";
    expect(handoff.startsWith("__Host-")).toBe(true);
  });

  it("answers 502 rather than starting an unconfigured session when the lifetime is missing", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: "" },
    );

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than guessing when the lifetime is not a number", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: "sixty-days" },
    );

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than accepting a zero or negative lifetime", async () => {
    const zero = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: "0" },
    );
    const negative = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: "-100" },
    );

    expect(zero.status).toBe(502);
    expect(negative.status).toBe(502);
  });

  it("never falls back to sixty days: a missing lifetime never sets a cookie at all", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: "" },
    );

    expect(setCookies(response)).toHaveLength(0);
  });

  it("answers 502 rather than shipping a Max-Age too large for a browser to honour", async () => {
    // 1 followed by 21 zeros: passes the digits-only shape check but renders via Number() as
    // exponent notation, which is not a valid Max-Age and is silently dropped by the browser.
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: `1${"0".repeat(21)}` },
    );

    expect(response.status).toBe(502);
  });

  it("accepts the two-year ceiling itself", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, SESSION_MAX_AGE_SECONDS: "63072000" },
    );

    expect(response.status).toBe(302);
  });

  it("answers 502 rather than accepting a whitespace-only cookie name prefix", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, COOKIE_NAME_PREFIX: " " },
    );

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than accepting a cookie name prefix that could break Set-Cookie", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      { ...env, COOKIE_NAME_PREFIX: "axonia;domain=evil.example" },
    );

    expect(response.status).toBe(502);
  });
});

describe("a deployment that cannot sign anybody in", () => {
  it("answers 502 rather than 401 when the session key is missing", async () => {
    const request = new Request("https://app.example.com/auth/login");

    const response = await handleAuth(
      request,
      { ...env, SESSION_COOKIE_KEY: "" },
      new URL(request.url),
    );

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than 401 when the client secret is missing", async () => {
    const request = new Request("https://app.example.com/auth/login");

    const response = await handleAuth(
      request,
      { ...env, OIDC_CLIENT_SECRET: "" },
      new URL(request.url),
    );

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than throwing when the issuer is missing", async () => {
    const request = new Request("https://app.example.com/auth/login");

    // Left unvalidated this reaches `new URL()` inside the login handler and throws
    // `TypeError: Invalid URL`, which surfaces as a 500 with no failure attributed at all.
    const response = await handleAuth(request, { ...env, OIDC_ISSUER: "" }, new URL(request.url));

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({ detail: expect.stringContaining("OIDC_ISSUER") });
  });

  it("answers 502 rather than throwing when the issuer is not an absolute URL", async () => {
    const request = new Request("https://app.example.com/auth/login");

    const response = await handleAuth(
      request,
      { ...env, OIDC_ISSUER: "login.example.com/realms/example" },
      new URL(request.url),
    );

    expect(response.status).toBe(502);
  });

  // Whoever sits on the path of a cleartext discovery request chooses where the client secret and
  // every refresh token go, so this is a deployment that cannot sign anybody in safely.
  it("answers 502 rather than asking a cleartext issuer anywhere but localhost for its endpoints", async () => {
    const response = await call("https://app.example.com/auth/login", undefined, {
      ...env,
      OIDC_ISSUER: "http://login.example.com/realms/example",
    });

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({ detail: expect.stringContaining("OIDC_ISSUER") });
    expect(identityProvider.discoveryRequests).toEqual([]);
  });

  it("answers 502 rather than admitting an issuer no browser could be redirected to", async () => {
    const request = new Request("https://app.example.com/auth/login");

    // Parses, so URL.canParse alone would admit it, and the authorize redirect would send the
    // browser somewhere it cannot follow.
    const response = await handleAuth(
      request,
      { ...env, OIDC_ISSUER: "ftp://login.example.com/realms/example" },
      new URL(request.url),
    );

    expect(response.status).toBe(502);
  });

  it("accepts the http issuer local development runs against", async () => {
    const localIssuer = "http://localhost:8081/realms/local";
    identityProvider.serve(localIssuer, keycloakLike(localIssuer));
    const request = new Request("http://localhost:3000/auth/login");

    const response = await handleAuth(
      request,
      { ...env, OIDC_ISSUER: localIssuer },
      new URL(request.url),
    );

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toContain(
      "http://localhost:8081/realms/local/protocol/openid-connect/auth",
    );
  });

  it("answers 502 when the session key is the wrong length to be a key", async () => {
    const request = new Request("https://app.example.com/auth/login");

    const response = await handleAuth(
      request,
      { ...env, SESSION_COOKIE_KEY: "far-too-short" },
      new URL(request.url),
    );

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than guessing when the scopes are missing", async () => {
    const response = await call("https://app.example.com/auth/login", undefined, {
      ...env,
      OIDC_SCOPES: "",
    });

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({ detail: expect.stringContaining("OIDC_SCOPES") });
  });

  it("answers 502 rather than signing in without an ID token when the scopes lack openid", async () => {
    const response = await call("https://app.example.com/auth/login", undefined, {
      ...env,
      OIDC_SCOPES: "profile email",
    });

    expect(response.status).toBe(502);
  });

  it("answers 502 rather than guessing how a session ends", async () => {
    const response = await call("https://app.example.com/auth/login", undefined, {
      ...env,
      OIDC_SESSION_END: "logout" as AuthEnv["OIDC_SESSION_END"],
    });

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({
      detail: expect.stringContaining("OIDC_SESSION_END"),
    });
  });

  it("answers 404 for a route under /auth that does not exist", async () => {
    expect((await call("https://app.example.com/auth/whatever")).status).toBe(404);
  });
});

/** An issuer that advertises every endpoint on a host of its own, so no URL can be derived from it. */
const GENERIC_ISSUER = "https://idp.example/tenant";

const GENERIC_IDP: DiscoveryDocument = {
  issuer: GENERIC_ISSUER,
  authorization_endpoint: "https://idp.example/authorize",
  token_endpoint: "https://idp.example/token",
  end_session_endpoint: "https://idp.example/logout",
  revocation_endpoint: "https://idp.example/revoke",
};

const genericEnv = (overrides: Partial<AuthEnv> = {}): AuthEnv => ({
  ...env,
  OIDC_ISSUER: GENERIC_ISSUER,
  ...overrides,
});

const ENTRA_ENV: AuthEnv = {
  ...env,
  OIDC_ISSUER: ENTRA_ISSUER,
  OIDC_SCOPES: "openid profile email offline_access api://app/axonia",
  OIDC_SESSION_END: "revoke",
};

const logout = async (customEnv: AuthEnv): Promise<Response> =>
  call(
    "https://app.example.com/auth/logout",
    { method: "POST", headers: { Cookie: await signedIn() } },
    customEnv,
  );

describe("discovery", () => {
  beforeEach(() => {
    identityProvider.serve(GENERIC_ISSUER, GENERIC_IDP);
    identityProvider.serve(ENTRA_ISSUER, ENTRA_LIKE);
  });

  it("asks the issuer where its endpoints are, at the well-known address", async () => {
    await call("https://app.example.com/auth/login");

    expect(identityProvider.discoveryRequests).toEqual([
      "https://login.example.com/realms/example/.well-known/openid-configuration",
    ]);
  });

  it("uses the authorization endpoint the issuer advertises", async () => {
    const response = await call("https://app.example.com/auth/login", undefined, genericEnv());

    const authorize = new URL(response.headers.get("Location") ?? "");
    expect(authorize.origin + authorize.pathname).toBe("https://idp.example/authorize");
  });

  it("exchanges the code at the token endpoint the issuer advertises", async () => {
    const { cookie, state } = await startedLogin("", genericEnv());

    await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
      genericEnv(),
    );

    expect(fetchCalls()[0]).toMatchObject({ url: GENERIC_IDP.token_endpoint, method: "POST" });
  });

  it("renews at the token endpoint the issuer advertises", async () => {
    await call(
      "https://app.example.com/auth/refresh",
      { method: "POST", headers: { Cookie: await signedIn() } },
      genericEnv(),
    );

    expect(fetchCalls()[0]).toMatchObject({ url: GENERIC_IDP.token_endpoint, method: "POST" });
  });

  it("requests the configured scopes", async () => {
    const response = await call("https://app.example.com/auth/login", undefined, ENTRA_ENV);

    expect(new URL(response.headers.get("Location") ?? "").searchParams.get("scope")).toBe(
      "openid profile email offline_access api://app/axonia",
    );
  });

  it("appends the organization a member asked for to the configured scopes", async () => {
    const response = await call(
      "https://app.example.com/auth/login?scope=openid+profile+email+organization%3Aacme",
      undefined,
      ENTRA_ENV,
    );

    expect(new URL(response.headers.get("Location") ?? "").searchParams.get("scope")).toBe(
      "openid profile email offline_access api://app/axonia organization:acme",
    );
  });

  it("asks once per isolate rather than once per request", async () => {
    await call("https://app.example.com/auth/login");
    await call("https://app.example.com/auth/login");
    await call("https://app.example.com/auth/refresh", {
      method: "POST",
      headers: { Cookie: await signedIn() },
    });

    expect(identityProvider.discoveryRequests).toHaveLength(1);
  });

  it("asks each issuer separately", async () => {
    await call("https://app.example.com/auth/login");
    await call("https://app.example.com/auth/login", undefined, genericEnv());

    expect(identityProvider.discoveryRequests).toHaveLength(2);
  });

  it("answers 503 rather than a broken redirect when the issuer cannot be reached", async () => {
    identityProvider.serve(GENERIC_ISSUER, () => {
      throw new TypeError("fetch failed");
    });

    const response = await call("https://app.example.com/auth/login", undefined, genericEnv());

    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({
      detail: "The identity provider could not be reached.",
    });
    expect(setCookies(response)).toHaveLength(0);
  });

  it("answers 503 when the issuer answers discovery with an error", async () => {
    identityProvider.serve(GENERIC_ISSUER, () => new Response(null, { status: 500 }));

    expect((await call("https://app.example.com/auth/login", undefined, genericEnv())).status).toBe(503);
  });

  // Cached as a failure, one outage would pin every request this isolate ever serves to a 503,
  // long after the identity provider came back.
  it("asks again after a failed discovery, so a passing outage does not pin a broken isolate", async () => {
    identityProvider.serve(GENERIC_ISSUER, () => {
      throw new TypeError("fetch failed");
    });
    const duringOutage = await call("https://app.example.com/auth/login", undefined, genericEnv());
    identityProvider.serve(GENERIC_ISSUER, GENERIC_IDP);

    const afterOutage = await call("https://app.example.com/auth/login", undefined, genericEnv());

    expect(duringOutage.status).toBe(503);
    expect(afterOutage.status).toBe(302);
    expect(identityProvider.discoveryRequests).toHaveLength(2);
  });

  // OIDC Discovery 1.0, section 4.3. A document naming another issuer is the wrong realm or
  // somebody else's answer, and neither may choose where this Worker sends its client secret.
  it("refuses a document that names a different issuer, and asks again next time", async () => {
    identityProvider.serve(GENERIC_ISSUER, { ...GENERIC_IDP, issuer: "https://idp.example/other" });
    const refused = await call("https://app.example.com/auth/login", undefined, genericEnv());
    identityProvider.serve(GENERIC_ISSUER, GENERIC_IDP);

    const accepted = await call("https://app.example.com/auth/login", undefined, genericEnv());

    expect(refused.status).toBe(503);
    expect(accepted.status).toBe(302);
    expect(identityProvider.discoveryRequests).toHaveLength(2);
  });

  it("refuses a document that names no issuer at all", async () => {
    const { issuer: _omitted, ...withoutIssuer } = GENERIC_IDP;
    identityProvider.serve(GENERIC_ISSUER, () => Response.json(withoutIssuer));

    expect((await call("https://app.example.com/auth/login", undefined, genericEnv())).status).toBe(503);
  });

  it("matches the issuer whether or not the configured value ends in a slash", async () => {
    const response = await call(
      "https://app.example.com/auth/login",
      undefined,
      genericEnv({ OIDC_ISSUER: `${GENERIC_ISSUER}/` }),
    );

    expect(response.status).toBe(302);
  });

  it("gives up on an issuer that does not answer in time, and asks again next time", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    const signals: (AbortSignal | null | undefined)[] = [];
    identityProvider.serve(GENERIC_ISSUER, (init) => {
      signals.push(init?.signal);
      throw new DOMException("The operation was aborted due to timeout", "TimeoutError");
    });
    const timedOut = await call("https://app.example.com/auth/login", undefined, genericEnv());
    identityProvider.serve(GENERIC_ISSUER, GENERIC_IDP);

    const afterwards = await call("https://app.example.com/auth/login", undefined, genericEnv());

    expect(timeout).toHaveBeenCalledTimes(2);
    expect(signals[0]).toBe(timeout.mock.results[0]?.value);
    expect(timedOut.status).toBe(503);
    expect(afterwards.status).toBe(302);
  });

  it("treats an optional endpoint published as null as absent", async () => {
    identityProvider.serve(GENERIC_ISSUER, () =>
      Response.json({ ...GENERIC_IDP, end_session_endpoint: null, revocation_endpoint: null }),
    );

    const login = await call("https://app.example.com/auth/login", undefined, genericEnv());
    const signedOut = await logout(genericEnv());

    expect(login.status).toBe(302);
    expect(await signedOut.json()).toEqual({ endSessionUrl: "https://app.example.com" });
    expect(fetchCalls()).toEqual([]);
  });

  it("refuses a document that advertises no token endpoint", async () => {
    identityProvider.serve(GENERIC_ISSUER, () =>
      Response.json({ authorization_endpoint: GENERIC_IDP.authorization_endpoint }),
    );

    expect((await call("https://app.example.com/auth/login", undefined, genericEnv())).status).toBe(503);
  });

  it("refuses a document that is not JSON at all", async () => {
    identityProvider.serve(GENERIC_ISSUER, () => new Response("<html>a captive portal</html>"));

    expect((await call("https://app.example.com/auth/login", undefined, genericEnv())).status).toBe(503);
  });

  // A cleartext token endpoint would carry the client secret and every refresh token in the open.
  it("refuses a cleartext endpoint anywhere but localhost", async () => {
    identityProvider.serve(GENERIC_ISSUER, {
      ...GENERIC_IDP,
      token_endpoint: "http://idp.example/token",
    });

    expect((await call("https://app.example.com/auth/login", undefined, genericEnv())).status).toBe(503);
  });

  it("refuses an end-session endpoint that is not a usable URL rather than redirecting a browser to it", async () => {
    identityProvider.serve(GENERIC_ISSUER, { ...GENERIC_IDP, end_session_endpoint: "javascript:alert(1)" });

    expect((await call("https://app.example.com/auth/login", undefined, genericEnv())).status).toBe(503);
  });

  it("keeps the session cookie when discovery fails during a renewal", async () => {
    identityProvider.serve(GENERIC_ISSUER, () => {
      throw new TypeError("fetch failed");
    });

    const response = await call(
      "https://app.example.com/auth/refresh",
      { method: "POST", headers: { Cookie: await signedIn() } },
      genericEnv(),
    );

    // The refresh token is not what failed, so it is not what gets thrown away.
    expect(response.status).toBe(503);
    expect(cookieNamed(response, SESSION_COOKIE)).toBeUndefined();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("answers 503 rather than restarting a login when discovery fails at the callback", async () => {
    const { cookie, state } = await startedLogin("", genericEnv());
    vi.resetModules();
    ({ handleAuth } = await import("./auth"));
    identityProvider.serve(GENERIC_ISSUER, () => {
      throw new TypeError("fetch failed");
    });

    const response = await call(
      `https://app.example.com/auth/callback?code=the-code&state=${state}`,
      { headers: { Cookie: cookie } },
      genericEnv(),
    );

    expect(response.status).toBe(503);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe("ending a session, by setting", () => {
  beforeEach(() => {
    identityProvider.serve(GENERIC_ISSUER, GENERIC_IDP);
    identityProvider.serve(ENTRA_ISSUER, ENTRA_LIKE);
  });

  it("keeps the Keycloak back-channel sign-out when configured", async () => {
    await logout({ ...env, OIDC_SESSION_END: "refresh-token-post" });

    expect(fetchCalls()).toEqual([
      expect.objectContaining({ url: KEYCLOAK_LIKE.end_session_endpoint, method: "POST" }),
    ]);
    expect(fetchCalls()[0]?.body.get("refresh_token")).toBe(REFRESH_TOKEN);
  });

  it("revokes the refresh token at the revocation endpoint in revoke mode", async () => {
    await logout(genericEnv({ OIDC_SESSION_END: "revoke" }));

    expect(fetchCalls()).toHaveLength(1);
    expect(fetchSpy.mock.calls[0]?.[1]?.redirect).toBe("manual");
    const [revocation] = fetchCalls();
    expect(revocation).toMatchObject({ url: GENERIC_IDP.revocation_endpoint, method: "POST" });
    expect(Object.fromEntries(revocation?.body ?? [])).toEqual({
      token: REFRESH_TOKEN,
      token_type_hint: "refresh_token",
      client_id: "web-app",
      client_secret: "a-client-secret",
    });
  });

  it("still signs out in revoke mode when the issuer advertises no revocation endpoint", async () => {
    const response = await logout(ENTRA_ENV);

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      endSessionUrl: expect.stringContaining(ENTRA_LIKE.end_session_endpoint),
    });
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
    expect(fetchCalls()).toEqual([]);
  });

  it("sends the browser to the end-session endpoint the issuer advertises, in either mode", async () => {
    const response = await logout(genericEnv({ OIDC_SESSION_END: "revoke" }));

    const { endSessionUrl } = (await response.json()) as { endSessionUrl: string };
    const end = new URL(endSessionUrl);
    expect(end.origin + end.pathname).toBe(GENERIC_IDP.end_session_endpoint);
    expect(end.searchParams.get("post_logout_redirect_uri")).toBe("https://app.example.com");
  });

  it("returns the browser to this origin when the issuer advertises no end-session endpoint", async () => {
    identityProvider.serve(GENERIC_ISSUER, { ...GENERIC_IDP, end_session_endpoint: undefined });

    const response = await logout(genericEnv());

    expect(await response.json()).toEqual({ endSessionUrl: "https://app.example.com" });
    expect(fetchCalls()).toEqual([]);
  });

  it("signs the member out of this browser even when discovery fails", async () => {
    identityProvider.serve(GENERIC_ISSUER, () => {
      throw new TypeError("fetch failed");
    });

    const response = await logout(genericEnv());

    expect(response.status).toBe(200);
    expect(cookieNamed(response, SESSION_COOKIE)).toContain("Max-Age=0");
    expect(await response.json()).toEqual({ endSessionUrl: "https://app.example.com" });
  });
});
