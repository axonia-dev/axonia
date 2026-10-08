import { mkdirSync, mkdtempSync, realpathSync, symlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ConfigurationError, readConfiguration } from "./config.ts";
import { environment, staticBuild } from "./test-support.ts";

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

const emptyDirectory = (): string => mkdtempSync(join(tmpdir(), "selfhosted-edge-empty-"));

describe("a complete configuration", () => {
  it("is read into what the edge runs on", async () => {
    const configuration = await readConfiguration(environment());

    expect(configuration).toMatchObject({
      port: 8080,
      publicOrigin: "https://axonia.example.com",
      edgeSharedSecret: "an-edge-secret",
      trustedProxyHops: 0,
      strictTransport: true,
      auth: { COOKIE_NAME_PREFIX: "axonia", OIDC_CLIENT_ID: "axonia", OIDC_SESSION_END: "revoke" },
    });
    expect(configuration.backendOrigin.href).toBe("http://127.0.0.1:9/");
  });

  it("reads the build's directory as the one a link to it names", async () => {
    const build = staticBuild();
    const link = join(emptyDirectory(), "app");
    symlinkSync(build, link);

    const { staticRoot } = await readConfiguration(environment({ STATIC_DIR: link }));

    expect(staticRoot).toBe(realpathSync(build));
  });

  it("names the identity provider in the policy and admits no inline script", async () => {
    const { contentSecurityPolicy } = await readConfiguration(environment());

    expect(contentSecurityPolicy).toBe(
      "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' blob:; font-src 'self'; connect-src 'self' https://login.example.com; " +
        "manifest-src 'self'; form-action 'self' https://login.example.com; " +
        "frame-ancestors 'none'; base-uri 'none'; object-src 'none'",
    );
  });

  it("reads a port and the number of proxies in front", async () => {
    const configuration = await readConfiguration(
      environment({ PORT: "0", TRUSTED_PROXY_HOPS: "2" }),
    );

    expect(configuration).toMatchObject({ port: 0, trustedProxyHops: 2 });
  });

  it("serves cleartext on localhost, and then asks for no strict transport", async () => {
    const configuration = await readConfiguration(
      environment({ PUBLIC_URL: "http://localhost:3000/" }),
    );

    expect(configuration).toMatchObject({
      publicOrigin: "http://localhost:3000",
      strictTransport: false,
    });
  });
});

describe("a configuration the edge refuses", () => {
  it("names every missing setting", async () => {
    expect(
      await problemsOf({
        OIDC_CLIENT_SECRET: undefined,
        SESSION_COOKIE_KEY: "",
        EDGE_SHARED_SECRET: "   ",
      }),
    ).toEqual([
      "OIDC_CLIENT_SECRET is required.",
      "SESSION_COOKIE_KEY is required.",
      "EDGE_SHARED_SECRET is required.",
    ]);
  });

  it("refuses a placeholder nobody resolved", async () => {
    expect(await problemsOf({ OIDC_CLIENT_SECRET: "${OIDC_CLIENT_SECRET}" })).toEqual([
      "OIDC_CLIENT_SECRET holds an unresolved placeholder.",
    ]);
  });

  it("names a malformed session key without printing it", async () => {
    const key = "a-session-key-nobody-generated";
    const refusal: unknown = await readConfiguration(
      environment({ SESSION_COOKIE_KEY: key }),
    ).catch((error: unknown) => error);

    expect(refusal).toBeInstanceOf(ConfigurationError);
    expect((refusal as ConfigurationError).problems).toEqual([
      "SESSION_COOKIE_KEY must be 32 bytes, base64url-encoded.",
    ]);
    expect((refusal as ConfigurationError).message).not.toContain(key);
  });

  it("reports every malformed setting at once, in the order the file is read", async () => {
    expect(
      await problemsOf({
        PORT: "eighty",
        PUBLIC_URL: "https://axonia.example.com/app",
        BACKEND_ORIGIN: "axonia-backend:8080",
        EDGE_SHARED_SECRET: "two words",
        STATIC_DIR: "relative/app",
        OIDC_SCOPES: "profile email",
      }),
    ).toEqual([
      "PORT must be a whole number from 0 to 65535.",
      "PUBLIC_URL must be an origin with no path, such as https://axonia.example.com.",
      "BACKEND_ORIGIN must be an origin with no path, such as http://axonia-backend:8080.",
      "EDGE_SHARED_SECRET must be printable ASCII with no spaces: it travels in a header.",
      "STATIC_DIR must be an absolute path to the built app, holding its index.html.",
      "OIDC_SCOPES must be configured as space-separated scopes that include openid.",
    ]);
  });

  it.each([
    [
      "http://axonia.example.com",
      "PUBLIC_URL must be https, or http on localhost: browsers refuse the session cookie anywhere else.",
    ],
    [
      "https://axonia.example.com?tenant=1",
      "PUBLIC_URL must be an origin with no path, such as https://axonia.example.com.",
    ],
    [
      "https://admin@axonia.example.com",
      "PUBLIC_URL must be an origin with no path, such as https://axonia.example.com.",
    ],
    [
      "axonia.example.com",
      "PUBLIC_URL must be an origin with no path, such as https://axonia.example.com.",
    ],
  ])("refuses %s as the public address", async (publicUrl, problem) => {
    expect(await problemsOf({ PUBLIC_URL: publicUrl })).toEqual([problem]);
  });

  it.each(["11", "-1", "1.5", "one"])("refuses %s trusted proxies", async (hops) => {
    expect(await problemsOf({ TRUSTED_PROXY_HOPS: hops })).toEqual([
      "TRUSTED_PROXY_HOPS must be a whole number from 0 to 10.",
    ]);
  });

  it("refuses a directory with no built app in it", async () => {
    const withIndexDirectory = emptyDirectory();
    mkdirSync(join(withIndexDirectory, "index.html"));

    for (const directory of [emptyDirectory(), withIndexDirectory]) {
      expect(await problemsOf({ STATIC_DIR: directory })).toEqual([
        "STATIC_DIR must be an absolute path to the built app, holding its index.html.",
      ]);
    }
  });

  it.each([
    [
      "OIDC_ISSUER",
      "http://login.example.com/a-tenant",
      "OIDC_ISSUER must be configured as an absolute https URL, or http on localhost.",
    ],
    [
      "SESSION_MAX_AGE_SECONDS",
      "sixty days",
      "SESSION_MAX_AGE_SECONDS must be configured as a positive whole number of seconds.",
    ],
    [
      "OIDC_SESSION_END",
      "logout",
      "OIDC_SESSION_END must be configured as refresh-token-post or revoke.",
    ],
  ])("relays the sign-in's own refusal of %s", async (name, value, problem) => {
    expect(await problemsOf({ [name]: value })).toEqual([problem]);
  });

  it("reads as one block an operator can act on", () => {
    expect(new ConfigurationError(["One.", "Two."]).message).toBe(
      "The edge cannot start:\n- One.\n- Two.",
    );
  });
});
