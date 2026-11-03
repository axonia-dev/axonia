import { join } from "node:path";
import { afterAll, describe, expect, it } from "vitest";
import { ConfigurationError, readConfiguration } from "./config.ts";
import { environment } from "./test-support.ts";
import { scratch } from "./test-scratch.ts";

const problemsOf = async (overrides: Record<string, string | undefined>): Promise<string[]> => {
  const refusal: unknown = await readConfiguration(environment(overrides)).then(
    () => null,
    (error: unknown) => error,
  );
  if (!(refusal instanceof ConfigurationError)) {
    throw new Error("The configuration was accepted.");
  }
  return [...refusal.problems];
};

const files = scratch();
const emptyDirectory = files.directory;
const tokenFile = files.tokenFile;

afterAll(() => {
  files.remove();
});

const FEDERATED_FILE_PROBLEM =
  "OIDC_FEDERATED_TOKEN_FILE must be an absolute path to a readable, non-empty file when OIDC_CLIENT_AUTH is federated-token-file.";
const PLATFORM_PROBLEM =
  "OIDC_CLIENT_AUTH=azure-managed-identity needs IDENTITY_ENDPOINT and IDENTITY_HEADER, which App Service and Container Apps set when the site has a managed identity.";
const IDENTITY_HEADER_PROBLEM =
  "IDENTITY_HEADER must be printable ASCII with no spaces: it travels in a header.";
const IDENTITY_HEADER = "a-header-nobody-may-log";
const MANAGED_IDENTITY = {
  OIDC_CLIENT_AUTH: "azure-managed-identity",
  OIDC_CLIENT_SECRET: "",
  OIDC_MANAGED_IDENTITY_CLIENT_ID: "a-managed-identity",
  IDENTITY_ENDPOINT: "http://127.0.0.1:42356/msi/token",
  IDENTITY_HEADER,
};

describe("how the client proves itself", () => {
  it.each([undefined, "", "  ", "client-secret"])(
    "is the secret when OIDC_CLIENT_AUTH is %j, and sets no port",
    async (mode) => {
      const { auth } = await readConfiguration(environment({ OIDC_CLIENT_AUTH: mode }));

      expect(auth.OIDC_CLIENT_SECRET).toBe("a-client-secret");
      expect(auth.clientAuthentication).toBeUndefined();
    },
  );

  it("requires the secret in client-secret mode", async () => {
    expect(await problemsOf({ OIDC_CLIENT_AUTH: "client-secret", OIDC_CLIENT_SECRET: "" })).toEqual(
      ["OIDC_CLIENT_SECRET is required."],
    );
  });

  it("refuses a mode it does not know", async () => {
    expect(await problemsOf({ OIDC_CLIENT_AUTH: "certificate" })).toEqual([
      "OIDC_CLIENT_AUTH must be client-secret, azure-managed-identity or federated-token-file.",
    ]);
  });

  it.each(["federated-token-file", "azure-managed-identity"])(
    "refuses a secret that %s would never send",
    async (mode) => {
      const problems = await problemsOf({
        ...MANAGED_IDENTITY,
        OIDC_CLIENT_AUTH: mode,
        OIDC_CLIENT_SECRET: "a-client-secret",
        OIDC_FEDERATED_TOKEN_FILE: tokenFile("a-token"),
      });

      expect(problems).toEqual([
        `OIDC_CLIENT_SECRET must not be set when OIDC_CLIENT_AUTH is ${mode}: the edge never sends it.`,
      ]);
      expect(problems.join()).not.toContain("a-client-secret");
    },
  );

  it("reads a federated token file, with no secret, into a port", async () => {
    const { auth } = await readConfiguration(
      environment({
        OIDC_CLIENT_AUTH: "federated-token-file",
        OIDC_CLIENT_SECRET: "",
        OIDC_FEDERATED_TOKEN_FILE: tokenFile("a-token\n"),
      }),
    );

    expect(auth.OIDC_CLIENT_SECRET).toBe("");
    await expect(auth.clientAuthentication?.()).resolves.toMatchObject({
      client_assertion: "a-token",
    });
  });

  it.each([
    ["a relative path", () => "token"],
    ["a missing file", () => join(emptyDirectory(), "missing")],
    ["an empty file", () => tokenFile("  \n")],
    ["a directory", () => emptyDirectory()],
    ["no path", () => ""],
  ])("refuses %s as the federated token file", async (_name, path) => {
    expect(
      await problemsOf({
        OIDC_CLIENT_AUTH: "federated-token-file",
        OIDC_CLIENT_SECRET: undefined,
        OIDC_FEDERATED_TOKEN_FILE: path(),
      }),
    ).toEqual([FEDERATED_FILE_PROBLEM]);
  });

  it("never prints the token file's content", async () => {
    const problems = await problemsOf({
      OIDC_CLIENT_AUTH: "federated-token-file",
      OIDC_CLIENT_SECRET: "a-client-secret",
      OIDC_FEDERATED_TOKEN_FILE: tokenFile("a-token-nobody-may-log"),
    });

    expect(problems.join()).not.toContain("a-token-nobody-may-log");
  });

  it("reads a managed identity into a port, with no secret", async () => {
    const { auth } = await readConfiguration(environment(MANAGED_IDENTITY));

    expect(auth.OIDC_CLIENT_SECRET).toBe("");
    expect(auth.clientAuthentication).toBeDefined();
  });

  it("requires the managed identity's client id", async () => {
    expect(
      await problemsOf({ ...MANAGED_IDENTITY, OIDC_MANAGED_IDENTITY_CLIENT_ID: undefined }),
    ).toEqual([
      "OIDC_MANAGED_IDENTITY_CLIENT_ID is required when OIDC_CLIENT_AUTH is azure-managed-identity.",
    ]);
  });

  it.each([
    ["no endpoint", { IDENTITY_ENDPOINT: undefined }],
    ["no header", { IDENTITY_HEADER: undefined }],
    ["an empty header", { IDENTITY_HEADER: " " }],
    ["a relative endpoint", { IDENTITY_ENDPOINT: "/msi/token" }],
    ["a file endpoint", { IDENTITY_ENDPOINT: "file:///msi/token" }],
  ])("refuses a managed identity with %s, never printing the header", async (_name, overrides) => {
    const problems = await problemsOf({ ...MANAGED_IDENTITY, ...overrides });

    expect(problems).toEqual([PLATFORM_PROBLEM]);
    expect(problems.join()).not.toContain(IDENTITY_HEADER);
  });

  it.each([
    ["a line break", "a-header\nnobody-may-log"],
    ["a space", "a-header nobody-may-log"],
    ["a non-ASCII character", "a-header-nobödy-may-log"],
  ])("refuses an identity header with %s, never printing it", async (_name, header) => {
    const problems = await problemsOf({ ...MANAGED_IDENTITY, IDENTITY_HEADER: header });

    expect(problems).toEqual([IDENTITY_HEADER_PROBLEM]);
    expect(problems.join()).not.toContain(header);
  });

  it("still reports the rest of the configuration beside a client problem", async () => {
    expect(
      await problemsOf({ OIDC_CLIENT_AUTH: "certificate", SESSION_COOKIE_KEY: "a-key" }),
    ).toEqual([
      "OIDC_CLIENT_AUTH must be client-secret, azure-managed-identity or federated-token-file.",
      "SESSION_COOKIE_KEY must be 32 bytes, base64url-encoded.",
    ]);
  });
});
