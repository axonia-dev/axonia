import { fileURLToPath, URL } from "node:url";

import mdx from "@mdx-js/rollup";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import {
  loadEnv,
  type PluginOption,
  type ProxyOptions,
  type UserConfig,
} from "vite";
import { configDefaults } from "vitest/config";

import { devBackendOrigin, devPort } from "./dev-auth.ts";

// Pin the unit-test suite to a fixed timezone west of UTC so date-rendering
// tests are deterministic across machines and CI. It must stay WEST of UTC
// (negative offset): a date-only ISO like "2026-01-09" parses as UTC midnight,
// so the local-frame regression (rendering the previous day) only surfaces when
// local time is behind UTC. Pinning UTC would make those regression tests
// vacuous. Set here in the main Vitest process so forked workers inherit it at
// startup, before any Date resolves.
process.env.TZ = "America/Los_Angeles";

// Locally there is no edge, so the dev proxy plays its part: it stamps the caller's address and the
// shared secret the backend's edge gate requires. The secret is the checked-in local constant both
// DEV profiles set; it protects nothing, it only keeps the local request path production's shape.
function backendProxy(target: string): ProxyOptions {
  return {
    target,
    changeOrigin: false,
    configure: (proxy) => {
      proxy.on("proxyReq", (proxyReq, req) => {
        proxyReq.setHeader(
          "X-Axonia-Client-Ip",
          req.socket.remoteAddress ?? "",
        );
        proxyReq.setHeader("X-Axonia-Edge-Auth", "local-dev-edge-secret");
      });
    },
  };
}

// Comma separated, with no VITE_ prefix, so it never reaches a bundle. Read from the development
// files whatever the mode: a hostname that reaches this machine belongs to the machine, not to an
// edition.
function devAllowedHosts(): string[] {
  const env = loadEnv("development", process.cwd(), "DEV_ALLOWED_HOSTS");
  return (env.DEV_ALLOWED_HOSTS ?? "")
    .split(",")
    .map((host) => host.trim())
    .filter((host) => host !== "");
}

/** What an edition's config decides; everything else is the same for both. */
export interface Edition {
  /** The dev server's stand-ins for the edge, ahead of every other plugin. */
  devPlugins: PluginOption[];
  /** The plugins that make this edition's build, after every other. */
  buildPlugins: PluginOption[];
  build: UserConfig["build"];
  /** True when the port is the identity provider's redirect URI and must never move. */
  strictPort: boolean;
  /** Sources this edition neither tests nor measures. */
  excludedSources: string[];
  /** Entry modules no test loads, left out of coverage. */
  uncoveredEntries: string[];
}

export function editionConfig(
  env: Record<string, string>,
  edition: Edition,
): UserConfig {
  const backend = backendProxy(devBackendOrigin(env));
  return {
    build: edition.build,
    plugins: [
      ...edition.devPlugins,
      { enforce: "pre", ...mdx({ providerImportSource: "@mdx-js/react" }) },
      react({ include: /\.(jsx|js|mdx|md|tsx|ts)$/ }),
      tailwindcss(),
      ...edition.buildPlugins,
    ],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      port: devPort(env),
      strictPort: edition.strictPort,
      allowedHosts: devAllowedHosts(),
      proxy: {
        "/axonia": backend,
        "/public": backend,
        // The docs connect-widget builds the MCP URL from window.location.origin, which is the dev
        // server in development, so the copy-pasted config works here as it does in production.
        "/mcp": backend,
        "/scim": backend,
        // The MCP OAuth surface. /oauth/authorize is deliberately absent: it is the SPA's consent page.
        // Anchored like the edge's path-boundary match, since a plain key here is a bare prefix.
        "^/oauth/(token|revoke)(?:[/?]|$)": backend,
        "^/\\.well-known/oauth-(protected-resource|authorization-server)(?:[/?]|$)":
          backend,
      },
    },
    test: {
      globals: true,
      css: false,
      // Before each test's beforeEach, every mock goes back to the implementation it was created
      // with, so a stub can never leak into a sibling test. A stub belongs in beforeEach or the test.
      mockReset: true,
      // Two suites, split by whether the code under test can even see a browser. Vitest builds a
      // fresh environment per test file, and a jsdom document costs about a second of that where a
      // bare Node context costs nothing measurable. The domain and the adapters are framework-free,
      // so a domain test that reaches for `document` fails here, which is the coupling the
      // architecture forbids. The handful of genuine exceptions say so in their own header with a
      // `@vitest-environment jsdom` docblock.
      projects: [
        {
          extends: true,
          test: {
            name: "node",
            environment: "node",
            include: [
              "src/**/domain/**/*.test.{ts,tsx}",
              "src/**/adapters/**/*.test.{ts,tsx}",
            ],
            exclude: [...configDefaults.exclude, ...edition.excludedSources],
          },
        },
        {
          extends: true,
          test: {
            name: "jsdom",
            environment: "jsdom",
            setupFiles: ["./src/test/setup.ts"],
            // Unit tests live in src as *.test.*; the Playwright specs under e2e/ are Playwright's.
            include: ["src/**/*.test.{ts,tsx}"],
            exclude: [
              "src/**/domain/**/*.test.{ts,tsx}",
              "src/**/adapters/**/*.test.{ts,tsx}",
              ...edition.excludedSources,
            ],
          },
        },
      ],
      coverage: {
        provider: "v8",
        reporter: ["text", "html"],
        include: ["src/**/*.{ts,tsx}"],
        exclude: [
          ...edition.uncoveredEntries,
          "src/test/**",
          "src/**/*.d.ts",
          "src/vite-env.d.ts",
          ...edition.excludedSources,
        ],
        thresholds: {
          lines: 90,
          statements: 90,
          functions: 90,
          branches: 80,
        },
      },
    },
  };
}
