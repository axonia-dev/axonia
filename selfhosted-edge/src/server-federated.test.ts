import { rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import type { AddressInfo } from "node:net";
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  onTestFinished,
  vi,
} from "vitest";
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
import { scratch } from "./test-scratch.ts";

const BODY_SECRET = "a-body-nobody-may-log";

const files = scratch();
let identity: FakeIdentityProvider;

beforeAll(async () => {
  identity = await fakeIdentityProvider();
});

afterAll(async () => {
  await identity.close();
  files.remove();
});

// The lines logged under one failure name, so a test can count them and read their cause.
const linesNamed = (lines: string, failure: string): string[] =>
  lines.split("\n").filter((line) => line.includes(`"failure":"${failure}"`));

const cookieOf = (setCookie: string | undefined): string => setCookie?.split(";")[0] ?? "";

describe("a sign-in with a federated client", () => {
  const TOKEN_FILE = files.tokenFile("a-token\n");
  const running: RunningEdge[] = [];

  const startFederated = async (overrides: Record<string, string | undefined>) => {
    const edge = await startEdge({
      OIDC_ISSUER: identity.issuer,
      OIDC_CLIENT_SECRET: undefined,
      ...overrides,
    });
    running.push(edge);
    return edge;
  };

  const signInAnswer = async (edge: RunningEdge) => {
    const login = await send(edge.origin, { path: "/auth/login?retry=1" });
    const state = new URL(login.headers.location ?? "").searchParams.get("state") ?? "";
    return send(edge.origin, {
      path: `/auth/callback?code=a-code&state=${encodeURIComponent(state)}`,
      headers: { cookie: cookieOf(login.headers["set-cookie"]?.[0]) },
    });
  };

  const completeSignIn = async (federated: RunningEdge): Promise<string> => {
    const callback = await signInAnswer(federated);
    return callback.status === 302 ? cookieOf(callback.headers["set-cookie"]?.[0]) : "";
  };

  beforeEach(() => {
    identity.tokenRequests.length = 0;
  });

  afterEach(async () => {
    vi.restoreAllMocks();
    await Promise.all(running.splice(0).map((edge) => edge.close()));
  });

  it("completes and renews a sign-in with a federated token file, sending no client secret", async () => {
    writeFileSync(TOKEN_FILE, "the-first-token\n");
    const federated = await startFederated({
      OIDC_CLIENT_AUTH: "federated-token-file",
      OIDC_FEDERATED_TOKEN_FILE: TOKEN_FILE,
    });

    const session = await completeSignIn(federated);
    writeFileSync(TOKEN_FILE, "the-rotated-token\n");
    const renewed = await send(federated.origin, {
      method: "POST",
      path: "/auth/refresh",
      headers: { cookie: session },
    });

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

  it("answers 503 and logs client_assertion_unavailable with the cause when the token file is gone", async () => {
    const vanishing = files.tokenFile("a-token-nobody-may-log\n");
    const federated = await startFederated({
      OIDC_CLIENT_AUTH: "federated-token-file",
      OIDC_FEDERATED_TOKEN_FILE: vanishing,
    });
    rmSync(vanishing);
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const callback = await signInAnswer(federated);

    expect(callback.status).toBe(503);
    const lines = loggedLines(log);
    const [failure, ...others] = linesNamed(lines, "client_assertion_unavailable");
    expect(others).toEqual([]);
    expect(failure).toContain("ENOENT");
    expect(failure).toContain(vanishing);
    expect(lines).not.toContain("identity_provider_unreachable");
    expect(lines).not.toContain("a-token-nobody-may-log");
    expect(identity.tokenRequests).toHaveLength(0);
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
      platform.reset();
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

    it("answers 503 and logs client_assertion_unavailable with the status when the identity endpoint fails", async () => {
      platform.answer = { status: 500, document: { error: BODY_SECRET } };
      const failing = await startFederated({
        OIDC_CLIENT_AUTH: "azure-managed-identity",
        OIDC_MANAGED_IDENTITY_CLIENT_ID: "a-failing-identity",
        IDENTITY_ENDPOINT: platform.endpoint,
        IDENTITY_HEADER,
      });
      const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

      const callback = await signInAnswer(failing);

      expect(callback.status).toBe(503);
      const lines = loggedLines(log);
      const [failure, ...others] = linesNamed(lines, "client_assertion_unavailable");
      expect(others).toEqual([]);
      expect(failure).toContain("The managed identity endpoint answered 500.");
      expect(lines).not.toContain("identity_provider_unreachable");
      expect(lines).not.toContain(IDENTITY_HEADER);
      expect(lines).not.toContain(BODY_SECRET);
      expect(lines).not.toContain(MANAGED_IDENTITY_TOKEN);
      expect(identity.tokenRequests).toHaveLength(0);
    });

    it("logs why the identity endpoint could not be reached, not only that the fetch failed", async () => {
      const dropping = createServer((connection) => connection.destroy());
      await new Promise<void>((resolve) => dropping.listen(0, "127.0.0.1", resolve));
      const { port } = dropping.address() as AddressInfo;
      onTestFinished(() => new Promise<void>((resolve) => dropping.close(() => resolve())));
      const unreachable = await startFederated({
        OIDC_CLIENT_AUTH: "azure-managed-identity",
        OIDC_MANAGED_IDENTITY_CLIENT_ID: "an-unreachable-identity",
        IDENTITY_ENDPOINT: `http://127.0.0.1:${port}/msi/token`,
        IDENTITY_HEADER,
      });
      const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

      const callback = await signInAnswer(unreachable);

      expect(callback.status).toBe(503);
      const [failure] = linesNamed(loggedLines(log), "client_assertion_unavailable");
      expect(JSON.parse(failure ?? "{}")).toMatchObject({
        error: { name: "TypeError", message: "fetch failed", cause: { code: "ECONNRESET" } },
      });
      expect(failure).not.toContain(IDENTITY_HEADER);
    });
  });
});
