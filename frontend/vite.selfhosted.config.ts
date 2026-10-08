import { fileURLToPath, URL } from "node:url";

import { defineConfig, loadEnv } from "vite";

import { devAuth } from "./dev-auth.ts";
import {
  SELF_HOSTED_DOCUMENT,
  SELF_HOSTED_MODE,
  SELF_HOSTED_OUT_DIR,
  selfHostedBuild,
} from "./selfhosted-build.ts";
import { editionConfig } from "./vite.shared.ts";

/**
 * The Self-hosted edition: one document and no Cloud module. A mode on the command line outranks
 * one a config returns, so it refuses every mode but its own rather than read another mode's env
 * files into the bundle.
 */
export default defineConfig(({ mode }) => {
  if (mode !== SELF_HOSTED_MODE) {
    throw new Error(
      "vite.selfhosted.config.ts runs in mode selfhosted: use the `:selfhosted` scripts.",
    );
  }
  const env = loadEnv(SELF_HOSTED_MODE, process.cwd(), "");
  return editionConfig(env, {
    devPlugins: [devAuth(env)],
    buildPlugins: [selfHostedBuild()],
    build: {
      outDir: SELF_HOSTED_OUT_DIR,
      rollupOptions: {
        input: {
          selfhosted: fileURLToPath(
            new URL(`./${SELF_HOSTED_DOCUMENT}`, import.meta.url),
          ),
        },
      },
    },
    // Its port is its identity provider's redirect URI, so it never moves to a free one.
    strictPort: true,
    excludedSources: ["src/cloud/**"],
    uncoveredEntries: ["src/selfhosted/main.tsx"],
  });
});
