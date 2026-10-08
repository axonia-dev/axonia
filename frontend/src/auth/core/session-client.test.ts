import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { jsonResponse, urlOf } from "@/test/fetch";

import {
  endSession,
  renewOnce,
  renewSession,
  startLogin,
} from "./session-client";

/** A token carrying the claims the profile is read out of. */
function tokenWith(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "none", typ: "JWT" }));
  return `${header}.${btoa(JSON.stringify(payload))}.signature`;
}

const ACCESS_TOKEN = tokenWith({
  sub: "alice-sub",
  email: "alice@example.com",
});

let assign: ReturnType<typeof vi.fn>;

beforeEach(() => {
  assign = vi.fn();
  // jsdom refuses a real navigation, so the one thing these functions do on the way out is
  // replaced wholesale rather than spied: location is not configurable per-property.
  Object.defineProperty(window, "location", {
    configurable: true,
    value: {
      assign,
      origin: "https://axonia.example.com",
      pathname: "/c/rust-practitioners",
      search: "",
      hash: "",
    },
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("renewSession", () => {
  it("spends the sealed cookie for an access token and reads the profile off the id token", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({
        accessToken: "an-access-token",
        expiresIn: 300,
        idToken: ACCESS_TOKEN,
      }),
    );

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toEqual({
      user: {
        access_token: "an-access-token",
        profile: { sub: "alice-sub", email: "alice@example.com" },
      },
      expiresIn: 300,
    });
  });

  it("sends the cookie along, which is the entire mechanism", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({ accessToken: "a", expiresIn: 300, idToken: ACCESS_TOKEN }),
    );

    // when
    await renewSession();

    // then
    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toBe("/auth/refresh");
    expect(init?.method).toBe("POST");
    expect(init?.credentials).toBe("same-origin");
  });

  it("falls back to the access token when Keycloak returned no id token", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({ accessToken: ACCESS_TOKEN, expiresIn: 60, idToken: null }),
    );

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toMatchObject({ user: { profile: { sub: "alice-sub" } } });
  });

  it("reports no session when the edge says the cookie is spent", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(jsonResponse({ status: 401 }, 401));

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toBeNull();
  });

  it("reports no session, without reading a body, when there never was a cookie", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toBeNull();
  });

  it("reports no session rather than a nameless one when the token carries no subject", async () => {
    // given - a token with no `sub` is not an identity, and treating it as one would sign somebody
    // in as nobody
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({
        accessToken: "a",
        expiresIn: 300,
        idToken: tokenWith({ email: "alice@example.com" }),
      }),
    );

    // when
    const renewed = await renewSession();

    // then
    expect(renewed).toBeNull();
  });
});

describe("renewOnce", () => {
  it("renews under a lock, so two tabs cannot spend one refresh token at the same instant", async () => {
    // given
    const request = vi.fn(
      async (_name: string, callback: () => Promise<unknown>) => callback(),
    );
    vi.stubGlobal("navigator", { ...navigator, locks: { request } });
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({ accessToken: "a", expiresIn: 300, idToken: ACCESS_TOKEN }),
    );

    // when
    await renewOnce("an-app.session-renewal");

    // then - the app's own lock name
    expect(request).toHaveBeenCalledWith(
      "an-app.session-renewal",
      expect.any(Function),
    );
    vi.unstubAllGlobals();
  });

  it("still renews where the Web Locks API is absent, rather than failing to sign anybody in", async () => {
    // given - jsdom has no lock manager, and a test environment must not be the reason sign-in
    // stops working
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({ accessToken: "a", expiresIn: 300, idToken: ACCESS_TOKEN }),
    );

    // when
    const renewed = await renewOnce("an-app.session-renewal");

    // then
    expect(renewed).toMatchObject({ user: { access_token: "a" } });
  });
});

describe("startLogin", () => {
  it("leaves the page for the edge, carrying where to come back to", () => {
    // when
    startLogin();

    // then
    const target = new URL(String(assign.mock.calls[0]?.[0]));
    expect(target.pathname).toBe("/auth/login");
    expect(target.searchParams.get("returnTo")).toBe("/c/rust-practitioners");
  });

  it("comes back to the query string and fragment too, because they hold what was on screen", () => {
    // given
    Object.assign(window.location, {
      pathname: "/communities/platform-guild/expertise",
      search: "?view=map&tag=postgres",
      hash: "#reading",
    });

    // when
    startLogin();

    // then
    const target = new URL(String(assign.mock.calls[0]?.[0]));
    expect(target.searchParams.get("returnTo")).toBe(
      "/communities/platform-guild/expertise?view=map&tag=postgres#reading",
    );
  });

  it("carries the organization scope, because a refresh grant can never add one", () => {
    // when
    startLogin({ scope: "openid profile email organization:acme" });

    // then
    const target = new URL(String(assign.mock.calls[0]?.[0]));
    expect(target.searchParams.get("scope")).toBe(
      "openid profile email organization:acme",
    );
  });

  it("prefers an explicit destination over wherever the person happens to be", () => {
    // when
    startLogin({ returnTo: "/discover" });

    // then
    const target = new URL(String(assign.mock.calls[0]?.[0]));
    expect(target.searchParams.get("returnTo")).toBe("/discover");
  });
});

describe("endSession", () => {
  it("navigates to the URL that ends the session at Keycloak, not only in this browser", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(
      jsonResponse({ endSessionUrl: "https://login.example.com/logout?x=1" }),
    );

    // when
    await endSession();

    // then
    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toBe("/auth/logout");
    expect(init?.credentials).toBe("same-origin");
    expect(assign).toHaveBeenCalledWith("https://login.example.com/logout?x=1");
  });

  it("still leaves the app when the edge could not name an end-session URL", async () => {
    // given - the same response that failed to produce a URL already cleared the cookie, so
    // staying put would show a signed-out member an app that looks signed in
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockResolvedValue(jsonResponse({}));

    // when
    await endSession();

    // then
    expect(assign).toHaveBeenCalledWith("https://axonia.example.com");
  });

  it("still leaves the app when the request itself failed", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    fetchSpy.mockRejectedValue(new Error("offline"));

    // when
    await endSession();

    // then
    expect(assign).toHaveBeenCalledWith("https://axonia.example.com");
  });
});
