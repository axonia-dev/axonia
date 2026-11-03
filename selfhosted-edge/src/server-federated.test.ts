import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import {
  type FakeIdentityEndpoint,
  type FakeIdentityProvider,
  fakeIdentityEndpoint,
  fakeIdentityProvider,
  IDENTITY_HEADER,
  loggedLines,
  MANAGED_IDENTITY_TOKEN,
  type RunningEdge,
  send,
  startEdge,
} from "./test-support.ts";

let identity: FakeIdentityProvider;

beforeAll(async () => {
  identity = await fakeIdentityProvider();
});

afterAll(async () => {
  await identity.close();
});

const cookieOf = (setCookie: string | undefined): string => setCookie?.split(";")[0] ?? "";

describe("a sign-in with a federated client", () => {
  const TOKEN_FILE = join(mkdtempSync(join(tmpdir(), "selfhosted-edge-token-")), "token");

  const completeSignIn = async (federated: RunningEdge): Promise<string> => {
    const login = await send(federated.origin, { path: "/auth/login?retry=1" });
    const state = new URL(login.headers.location ?? "").searchParams.get("state") ?? "";
    const callback = await send(federated.origin, {
      path: `/auth/callback?code=a-code&state=${encodeURIComponent(state)}`,
      headers: { cookie: cookieOf(login.headers["set-cookie"]?.[0]) },
    });
    return callback.status === 302 ? cookieOf(callback.headers["set-cookie"]?.[0]) : "";
  };

  beforeEach(() => {
    identity.tokenRequests.length = 0;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("completes and renews a sign-in with a federated token file, sending no client secret", async () => {
    writeFileSync(TOKEN_FILE, "the-first-token\n");
    const federated = await startEdge({
      OIDC_ISSUER: identity.issuer,
      OIDC_CLIENT_AUTH: "federated-token-file",
      OIDC_FEDERATED_TOKEN_FILE: TOKEN_FILE,
      OIDC_CLIENT_SECRET: undefined,
    });

    const session = await completeSignIn(federated);
    writeFileSync(TOKEN_FILE, "the-rotated-token\n");
    const renewed = await send(federated.origin, {
      method: "POST",
      path: "/auth/refresh",
      headers: { cookie: session },
    });
    await federated.close();

    expect(renewed.status).toBe(200);
    const [signIn, renewal] = identity.tokenRequests;
    expect(signIn?.get("client_assertion")).toBe("the-first-token");
    expect(renewal?.get("client_assertion")).toBe("the-rotated-token");
    for (const request of identity.tokenRequests) {
      expect(request.get("client_assertion_type")).toBe(
        "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
      );
      expect(request.has("client_secret")).toBe(false);
    }
  });

  describe("through an Azure managed identity", () => {
    let platform: FakeIdentityEndpoint;
    let federated: RunningEdge;

    beforeAll(async () => {
      platform = await fakeIdentityEndpoint();
      federated = await startEdge({
        OIDC_ISSUER: identity.issuer,
        OIDC_CLIENT_AUTH: "azure-managed-identity",
        OIDC_MANAGED_IDENTITY_CLIENT_ID: "a-managed-identity",
        IDENTITY_ENDPOINT: platform.endpoint,
        IDENTITY_HEADER,
        OIDC_CLIENT_SECRET: undefined,
      });
    });

    afterAll(async () => {
      await federated.close();
      await platform.close();
    });

    afterEach(() => {
      platform.received.length = 0;
      platform.answer = { ...platform.answer, status: 200 };
    });

    it("completes a sign-in with an Azure managed identity", async () => {
      const session = await completeSignIn(federated);

      expect(session).not.toBe("");
      const [asked] = platform.received;
      expect(asked?.identityHeader).toBe(IDENTITY_HEADER);
      const query = new URL(asked?.url ?? "", "http://platform").searchParams;
      expect(query.get("resource")).toBe("api://AzureADTokenExchange");
      expect(query.get("client_id")).toBe("a-managed-identity");
      expect(identity.tokenRequests.at(-1)?.get("client_assertion")).toBe(MANAGED_IDENTITY_TOKEN);
      expect(identity.tokenRequests.at(-1)?.has("client_secret")).toBe(false);
    });

    it("answers 503 and logs client_assertion_unavailable when the identity endpoint fails", async () => {
      platform.answer = { status: 500, document: { error: "boom" } };
      const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);
      const failing = await startEdge({
        OIDC_ISSUER: identity.issuer,
        OIDC_CLIENT_AUTH: "azure-managed-identity",
        OIDC_MANAGED_IDENTITY_CLIENT_ID: "a-failing-identity",
        IDENTITY_ENDPOINT: platform.endpoint,
        IDENTITY_HEADER,
        OIDC_CLIENT_SECRET: undefined,
      });

      const login = await send(failing.origin, { path: "/auth/login?retry=1" });
      const state = new URL(login.headers.location ?? "").searchParams.get("state") ?? "";
      const callback = await send(failing.origin, {
        path: `/auth/callback?code=a-code&state=${encodeURIComponent(state)}`,
        headers: { cookie: cookieOf(login.headers["set-cookie"]?.[0]) },
      });
      await failing.close();

      expect(callback.status).toBe(503);
      const lines = loggedLines(log);
      expect(lines).toContain('"failure":"client_assertion_unavailable"');
      expect(lines).not.toContain("identity_provider_unreachable");
      expect(lines).not.toContain(IDENTITY_HEADER);
      expect(lines).not.toContain(MANAGED_IDENTITY_TOKEN);
      expect(identity.tokenRequests).toHaveLength(0);
    });
  });
});
