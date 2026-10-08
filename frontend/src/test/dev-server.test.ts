/// <reference types="node" />
import { loadConfigFromFile, type ViteDevServer } from "vite";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  devAuth,
  devBackendOrigin,
  devCookiePrefix,
  devPort,
  devScopes,
  devSessionEnd,
} from "../../dev-auth";
import {
  devAuthMiddleware,
  type DevAuthRoute,
} from "../../dev-auth-middleware";

vi.mock("../../dev-auth-middleware", () => ({
  devAuthMiddleware: vi.fn(),
}));

const SELF_HOSTED_ENV = {
  DEV_BACKEND_ORIGIN: "http://localhost:8090",
  DEV_PORT: "3200",
  DEV_COOKIE_NAME_PREFIX: "axonia-selfhosted",
  DEV_OIDC_ISSUER: "http://localhost:8091/axonia",
  DEV_OIDC_CLIENT_ID: "axonia-local",
  DEV_OIDC_SCOPES: "openid profile email offline_access",
  DEV_OIDC_SESSION_END: "revoke",
};

const configureServerOf = (
  plugin: ReturnType<typeof devAuth>,
): ((server: ViteDevServer) => void) =>
  plugin.configureServer as (server: ViteDevServer) => void;

// A dev server runs with no DEV_* overrides unless its mode's env names them, so two of them run
// side by side without sharing a port, a backend, a session cookie or an identity provider.
describe("the dev server's settings", () => {
  it("default to a backend on 8080, a dev server on 3000 and a Keycloak-shaped sign-in", () => {
    expect(devBackendOrigin({})).toBe("http://localhost:8080");
    expect(devPort({})).toBe(3000);
    expect(devCookiePrefix({})).toBe("axonia");
    expect(devScopes({})).toBe("openid profile email");
    expect(devSessionEnd({})).toBe("refresh-token-post");
  });

  it("follow the mode's env", () => {
    expect(devBackendOrigin(SELF_HOSTED_ENV)).toBe("http://localhost:8090");
    expect(devPort(SELF_HOSTED_ENV)).toBe(3200);
    expect(devCookiePrefix(SELF_HOSTED_ENV)).toBe("axonia-selfhosted");
    expect(devScopes(SELF_HOSTED_ENV)).toBe(
      "openid profile email offline_access",
    );
    expect(devSessionEnd(SELF_HOSTED_ENV)).toBe("revoke");
  });

  it.each(["", "0", "3200x", "-1", "70000"])("refuses the port %j", (port) => {
    expect(() => devPort({ DEV_PORT: port })).toThrow(
      `DEV_PORT must be a port number, got "${port}"`,
    );
  });

  it.each(["", "end-session", "Revoke"])(
    "refuses the sign-out %j",
    (sessionEnd) => {
      expect(() => devSessionEnd({ DEV_OIDC_SESSION_END: sessionEnd })).toThrow(
        `DEV_OIDC_SESSION_END must be refresh-token-post or revoke, got "${sessionEnd}"`,
      );
    },
  );
});

describe("the dev server's sign-in", () => {
  it("signs in through the mode's identity provider, under its own cookie prefix, on its own port", () => {
    // given
    const configureServer = configureServerOf(devAuth(SELF_HOSTED_ENV));

    // when
    configureServer({ middlewares: { use: vi.fn() } } as never);

    // then
    expect(devAuthMiddleware).toHaveBeenCalledWith(
      expect.objectContaining({
        OIDC_ISSUER: "http://localhost:8091/axonia",
        OIDC_CLIENT_ID: "axonia-local",
        OIDC_SCOPES: "openid profile email offline_access",
        OIDC_SESSION_END: "revoke",
        COOKIE_NAME_PREFIX: "axonia-selfhosted",
      }),
      "localhost:3200",
      [],
    );
  });

  it("hands the middleware the routes it is given", () => {
    // given
    const route: DevAuthRoute = {
      matches: () => false,
      answer: () => Promise.resolve(new Response()),
    };
    const configureServer = configureServerOf(devAuth({}, [route]));

    // when
    configureServer({ middlewares: { use: vi.fn() } } as never);

    // then
    expect(devAuthMiddleware).toHaveBeenCalledWith(
      expect.anything(),
      "localhost:3000",
      [route],
    );
  });
});

describe("the Self-hosted dev server", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  // Loaded the way `pnpm dev:selfhosted` loads it, by Vite rather than through this suite's transform, which rewrites
  // the config's `new URL(..., import.meta.url)` into a URL on the test document. The config reads its env with
  // loadEnv(mode, cwd, ""), where a process variable outranks every .env file, so the stubs below decide what it reads
  // whatever a developer's shell or .env.selfhosted.local holds. The config imports the plugins it builds (MDX, Tailwind,
  // React), most of the cost: under half a second idle, several on a loaded runner, hence the explicit timeout.
  it(
    "proxies to the backend its env names, on a port it never gives up",
    { timeout: 30_000 },
    async () => {
      // given
      vi.stubEnv("DEV_PORT", "3299");
      vi.stubEnv("DEV_BACKEND_ORIGIN", "http://localhost:8099");

      // when
      const actual = await loadConfigFromFile(
        { command: "serve", mode: "selfhosted" },
        "vite.selfhosted.config.ts",
        undefined,
        "silent",
      );

      // then
      expect(actual?.config.server?.port).toBe(3299);
      expect(actual?.config.server?.strictPort).toBe(true);
      expect(actual?.config.server?.proxy?.["/axonia"]).toMatchObject({
        target: "http://localhost:8099",
      });
    },
  );

  // A mode on the command line outranks one a config returns, so the config refuses every other
  // mode rather than building the open bundle from another mode's env files.
  it("refuses any mode but its own", { timeout: 30_000 }, async () => {
    // when
    const loading = loadConfigFromFile(
      { command: "build", mode: "production" },
      "vite.selfhosted.config.ts",
      undefined,
      "silent",
    );

    // then
    await expect(loading).rejects.toThrow(
      "vite.selfhosted.config.ts runs in mode selfhosted: use the `:selfhosted` scripts.",
    );
  });
});
