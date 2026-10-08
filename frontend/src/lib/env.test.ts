import { afterEach, describe, expect, it, vi } from "vitest";

import { parseEnv, resolveApiBase } from "@/lib/env";

const valid = {
  VITE_API_BASE_URL: "http://localhost:8080",
};

describe("parseEnv", () => {
  it("parses a valid environment", () => {
    // when
    const actual = parseEnv(valid);

    // then
    expect(actual).toEqual(valid);
  });

  it("accepts an environment that names no API base url", () => {
    // when
    const actual = parseEnv({});

    // then
    expect(actual.VITE_API_BASE_URL).toBeUndefined();
  });

  it("throws when the API base url is not a url", () => {
    // when / then
    expect(() =>
      parseEnv({ ...valid, VITE_API_BASE_URL: "not-a-url" }),
    ).toThrow();
  });

  it("carries no identity-provider configuration for the browser to hold", () => {
    // given - the browser is not the OAuth client, and an authority or client id reaching the bundle
    // again would mean something had started talking to the identity provider directly

    // when
    const actual = parseEnv({
      ...valid,
      VITE_OIDC_AUTHORITY: "http://localhost:8091/axonia",
      VITE_OIDC_CLIENT_ID: "axonia-local",
    });

    // then - unknown keys are dropped rather than carried
    expect(actual).not.toHaveProperty("VITE_OIDC_AUTHORITY");
    expect(actual).not.toHaveProperty("VITE_OIDC_CLIENT_ID");
  });
});

describe("resolveApiBase", () => {
  it("reaches the API on the page's own origin when the build names none", () => {
    // when
    const actual = resolveApiBase(undefined, "https://axonia.acme.example");

    // then
    expect(actual).toBe("https://axonia.acme.example");
  });

  it("keeps the address a build names, wherever the page is served from", () => {
    // when
    const actual = resolveApiBase(
      "https://api.example.com",
      "https://elsewhere.example.com",
    );

    // then
    expect(actual).toBe("https://api.example.com");
  });
});

describe("apiBaseUrl", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("reaches the API at the address the build names, not the page's own origin", async () => {
    // given
    vi.stubEnv("VITE_API_BASE_URL", "https://api.example.com");
    vi.resetModules();
    const { apiBaseUrl } = await import("@/lib/env");

    // when
    const actual = apiBaseUrl();

    // then
    expect(actual).toBe("https://api.example.com");
  });
});
