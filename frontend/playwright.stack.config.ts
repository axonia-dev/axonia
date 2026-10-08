import { defineConfig, devices } from "@playwright/test";

/**
 * The Self-hosted edition end to end, against deploy/compose.yaml and deploy/compose.test.yaml,
 * which must be up first: the specs share one installation, in order.
 */
export default defineConfig({
  testDir: "./e2e/stack",
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://localhost:18083",
    locale: "en-US",
    timezoneId: "UTC",
    colorScheme: "light",
    trace: "retain-on-failure",
  },
});
