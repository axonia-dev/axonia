import { defineConfig, devices, type Project } from "@playwright/test";

const PORT = 4174;

/**
 * Every spec both editions share and the open edition's own, against its own build. The Cloud's
 * specs are not part of this edition, and the stack's need a running installation.
 */
export const selfHostedProject: Project = {
  name: "selfhosted",
  testDir: "./e2e",
  testIgnore: /[\\/]e2e[\\/](cloud|stack)[\\/]/,
  use: { ...devices["Desktop Chrome"], baseURL: `http://localhost:${PORT}` },
};

export const selfHostedServer = {
  command: `pnpm build:selfhosted && pnpm exec vite preview --config vite.selfhosted.config.ts --mode selfhosted --port ${PORT} --strictPort`,
  url: `http://localhost:${PORT}`,
  reuseExistingServer: !process.env.CI,
  // It covers the build as well as the server coming up, and the build is the slow half.
  timeout: 240_000,
};

export default defineConfig({
  fullyParallel: true,
  reporter: "list",
  use: { trace: "retain-on-failure", colorScheme: "light" },
  projects: [selfHostedProject],
  webServer: selfHostedServer,
});
