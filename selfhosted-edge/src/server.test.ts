import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { publicUrlOf } from "./server.ts";
import { oauthCookieName, sessionCookieName } from "./shared.ts";
import {
  deadOrigin,
  type FakeIdentityProvider,
  fakeIdentityProvider,
  GRANTED,
  loggedLines,
  type RunningEdge,
  send,
  startEdge,
} from "./test-support.ts";

let identity: FakeIdentityProvider;
let edge: RunningEdge;

beforeAll(async () => {
  identity = await fakeIdentityProvider();
  edge = await startEdge({ OIDC_ISSUER: identity.issuer });
});

afterAll(async () => {
  await edge.close();
  await identity.close();
});

const cookieOf = (setCookie: string | undefined): string => setCookie?.split(";")[0] ?? "";

describe("the health check", () => {
  it("is answered by the edge alone", async () => {
    const answer = await send(edge.origin, { path: "/healthz" });

    expect(answer.status).toBe(200);
    expect(answer.body).toBe("ok");
    expect(answer.headers["cache-control"]).toBe("no-store");
  });
});

describe("a sign-in", () => {
  it("starts at the provider, for the public address whatever Host says", async () => {
    const answer = await send(edge.origin, {
      path: "/auth/login?returnTo=/communities",
      headers: { host: "evil.example" },
    });
    const authorize = new URL(answer.headers.location ?? "");

    expect(answer.status).toBe(302);
    expect(`${authorize.origin}${authorize.pathname}`).toBe(`${identity.issuer}/authorize`);
    expect(authorize.searchParams.get("redirect_uri")).toBe(
      "https://axonia.example.com/auth/callback",
    );
    expect(authorize.searchParams.get("scope")).toBe("openid profile email offline_access");
    expect(answer.headers["set-cookie"]?.[0]).toMatch(new RegExp(`^${oauthCookieName("axonia")}=`));
    expect(answer.headers["content-security-policy"]).toContain(
      `connect-src 'self' ${new URL(identity.issuer).origin}`,
    );
  });

  it("completes into a sealed session cookie, and renews from it", async () => {
    const login = await send(edge.origin, { path: "/auth/login?returnTo=/communities" });
    const state = new URL(login.headers.location ?? "").searchParams.get("state") ?? "";

    const callback = await send(edge.origin, {
      path: `/auth/callback?code=a-code&state=${encodeURIComponent(state)}`,
      headers: { cookie: cookieOf(login.headers["set-cookie"]?.[0]) },
    });
    const [session, handoffCleared] = callback.headers["set-cookie"] ?? [];

    expect(callback.status).toBe(302);
    expect(callback.headers.location).toBe("/communities");
    expect(session).toMatch(
      new RegExp(
        `^${sessionCookieName("axonia")}=[A-Za-z0-9_-]+; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=5184000$`,
      ),
    );
    expect(handoffCleared).toMatch(new RegExp(`^${oauthCookieName("axonia")}=; `));
    expect(identity.tokenRequests.at(-1)?.get("redirect_uri")).toBe(
      "https://axonia.example.com/auth/callback",
    );

    const renewed = await send(edge.origin, {
      method: "POST",
      path: "/auth/refresh",
      headers: { cookie: cookieOf(session) },
    });

    expect(renewed.status).toBe(200);
    expect(JSON.parse(renewed.body)).toMatchObject({ accessToken: "an-access-token" });
  });

  it("answers a signed-out renewal with 204", async () => {
    expect((await send(edge.origin, { method: "POST", path: "/auth/refresh" })).status).toBe(204);
  });

  it("refuses a method no sign-in route takes", async () => {
    const answer = await send(edge.origin, { method: "DELETE", path: "/auth/logout" });

    expect(answer.status).toBe(405);
    expect(answer.headers.allow).toBe("GET, POST");
  });

  it("is a 503 while the provider is down, logged as such", async () => {
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);
    const stranded = await startEdge({ OIDC_ISSUER: `${await deadOrigin()}/a-tenant` });

    const answer = await send(stranded.origin, { path: "/auth/login" });
    await stranded.close();

    expect(answer.status).toBe(503);
    expect(loggedLines(log)).toContain('"failure":"identity_provider_unreachable"');
  });
});

describe("a client the provider refuses", () => {
  const signIn = async (query = ""): Promise<{ status: number; body: string; session: string }> => {
    const login = await send(edge.origin, { path: `/auth/login${query}` });
    const state = new URL(login.headers.location ?? "").searchParams.get("state") ?? "";
    const callback = await send(edge.origin, {
      path: `/auth/callback?code=a-code&state=${encodeURIComponent(state)}`,
      headers: { cookie: cookieOf(login.headers["set-cookie"]?.[0]) },
    });
    return {
      status: callback.status,
      body: callback.body,
      session: cookieOf(callback.headers["set-cookie"]?.[0]),
    };
  };

  const renew = (session: string): ReturnType<typeof send> =>
    send(edge.origin, { method: "POST", path: "/auth/refresh", headers: { cookie: session } });

  const refusing = (status: number, error: string): void => {
    identity.tokenAnswer = {
      status,
      document: { error, error_description: "the client secret expired on 2026-10-01" },
    };
  };

  afterEach(() => {
    identity.tokenAnswer = GRANTED;
    vi.restoreAllMocks();
  });

  it("ends a restarted sign-in on a 502, logged with the provider's code and nothing else of its answer", async () => {
    refusing(401, "invalid_client");
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answer = await signIn("?retry=1");

    expect(answer.status).toBe(502);
    expect(JSON.parse(answer.body)).toMatchObject({
      detail:
        "The identity provider refused to complete the sign-in. The edge's client settings may be wrong.",
    });
    expect(loggedLines(log)).toBe(
      '{"level":"error","failure":"token_refused","method":"GET","path":"/auth/callback","oauth_error":"invalid_client"}\n',
    );
  });

  it("logs a restarted sign-in refused with no readable code, and names none", async () => {
    identity.tokenAnswer = { status: 400, document: { error_description: "invalid_client" } };
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answer = await signIn("?retry=1");

    expect(answer.status).toBe(502);
    expect(loggedLines(log)).toBe(
      '{"level":"error","failure":"token_refused","method":"GET","path":"/auth/callback"}\n',
    );
  });

  it("ends a restarted sign-in granted no refresh token on a 502 that names offline_access, logged as such", async () => {
    identity.tokenAnswer = { status: 200, document: { access_token: "an-access-token" } };
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answer = await signIn("?retry=1");

    expect(answer.status).toBe(502);
    expect(JSON.parse(answer.body)).toMatchObject({
      detail: "The identity provider issued no refresh token. OIDC_SCOPES may lack offline_access.",
    });
    expect(loggedLines(log)).toBe(
      '{"level":"error","failure":"no_refresh_token","method":"GET","path":"/auth/callback"}\n',
    );
  });

  // Anyone can start a restarted sign-in and bring back a junk code.
  it("answers five junk codes on restarted sign-ins with a 400 each and logs none of them", async () => {
    refusing(400, "invalid_grant");
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answers = [];
    for (let attempt = 0; attempt < 5; attempt++) {
      answers.push(await signIn("?retry=1"));
    }

    expect(answers.map(({ status }) => status)).toEqual([400, 400, 400, 400, 400]);
    expect(JSON.parse(answers[0]?.body ?? "")).toMatchObject({
      detail: "The sign-in could not be completed. Start it again.",
    });
    expect(log).not.toHaveBeenCalled();
  });

  it("logs a renewal refused as invalid_client, which ends every session rather than one", async () => {
    const { session } = await signIn();
    refusing(401, "invalid_client");
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answer = await renew(session);

    expect(answer.status).toBe(401);
    expect(loggedLines(log)).toBe(
      '{"level":"error","failure":"token_refused","method":"POST","path":"/auth/refresh","oauth_error":"invalid_client"}\n',
    );
  });

  it("logs nothing for a renewal refused as invalid_grant, one member's session ending", async () => {
    const { session } = await signIn();
    refusing(400, "invalid_grant");
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answer = await renew(session);

    expect(answer.status).toBe(401);
    expect(log).not.toHaveBeenCalled();
  });
});

describe("a request target", () => {
  it.each([
    ["GET", "http://evil.example/axonia/me"],
    ["OPTIONS", "*"],
  ])("that is not a path is refused: %s %s", async (method, path) => {
    const answer = await send(edge.origin, { method, path });

    expect(answer.status).toBe(400);
    expect(answer.headers["content-security-policy"]).toBe(
      edge.configuration.contentSecurityPolicy,
    );
  });

  it("that starts with two slashes is a path on this host, not another host", async () => {
    const answer = await send(edge.origin, { path: "//evil.example/auth/login" });

    expect(answer.status).toBe(404);
    expect(answer.headers).not.toHaveProperty("location");
  });

  it.each([
    ["/a?b=1", "https://axonia.example.com/a?b=1"],
    ["//evil.example/x", "https://axonia.example.com//evil.example/x"],
    ["http://evil.example/", null],
    ["*", null],
    [undefined, null],
  ])("%s is read as %s", (target, href) => {
    expect(publicUrlOf(target, "https://axonia.example.com")?.href ?? null).toBe(href);
  });
});
