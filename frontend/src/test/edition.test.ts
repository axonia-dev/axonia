/// <reference types="node" />
import { spawnSync } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";

import { afterAll, beforeAll, describe, expect, it } from "vitest";

const EDITION = resolve(import.meta.dirname, "../../edition.mjs");
const ROOT = resolve(import.meta.dirname, "../../node_modules/.tmp/edition");
const CLOUD = join(ROOT, "cloud");
const SELF_HOSTED = join(ROOT, "selfhosted");
const PNPM = join(ROOT, "pnpm.cjs");
const PACKAGE = JSON.stringify({
  scripts: {
    "dev:cloud": "vite",
    "dev:selfhosted": "vite --config vite.selfhosted.config.ts",
    "og:card:cloud": "node scripts/og-card.mjs",
  },
});

beforeAll(() => {
  rmSync(ROOT, { recursive: true, force: true });
  mkdirSync(join(CLOUD, "src", "cloud"), { recursive: true });
  mkdirSync(SELF_HOSTED, { recursive: true });
  writeFileSync(join(CLOUD, "package.json"), PACKAGE);
  writeFileSync(join(SELF_HOSTED, "package.json"), PACKAGE);
  writeFileSync(PNPM, "console.log(process.argv.slice(2).join(' '));");
});

afterAll(() => {
  rmSync(ROOT, { recursive: true, force: true });
});

function run(checkout: string, ...args: string[]) {
  return spawnSync(process.execPath, [EDITION, ...args], {
    cwd: checkout,
    encoding: "utf8",
    env: { ...process.env, npm_execpath: PNPM },
  });
}

describe("edition.mjs", () => {
  it("runs the Cloud's script in a checkout holding src/cloud", () => {
    const result = run(CLOUD, "dev", "--port", "3000");

    expect(result.status).toBe(0);
    expect(result.stdout.trim()).toBe("run dev:cloud --port 3000");
  });

  it("runs the Self-hosted script in a checkout without src/cloud", () => {
    const result = run(SELF_HOSTED, "dev");

    expect(result.status).toBe(0);
    expect(result.stdout.trim()).toBe("run dev:selfhosted");
  });

  it("refuses a script the checkout's edition does not have", () => {
    const result = run(SELF_HOSTED, "og:card");

    expect(result.status).toBe(1);
    expect(result.stderr.trim()).toBe(
      "No selfhosted script for og:card in this checkout.",
    );
  });
});
