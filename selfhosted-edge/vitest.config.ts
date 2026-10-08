import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
    restoreMocks: true,
    coverage: {
      provider: "v8",
      include: ["src/**/*.ts"],
      // main.ts and precompress-main.ts run in a process of their own (main.test.ts,
      // precompress-main.test.ts), out of the collector's sight.
      exclude: [
        "src/**/*.test.ts",
        "src/test-support.ts",
        "src/main.ts",
        "src/precompress-main.ts",
      ],
      thresholds: { lines: 100, statements: 100, functions: 100, branches: 90 },
    },
  },
});
