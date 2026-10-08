import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// The image's build runs this file with Node alone, so the test does too.
const MAIN = fileURLToPath(new URL("./precompress-main.ts", import.meta.url));

const run = async (args: string[]) => {
  const child = spawn(process.execPath, [MAIN, ...args], { stdio: ["ignore", "pipe", "pipe"] });
  let stdout = "";
  let stderr = "";
  child.stdout.on("data", (chunk: Buffer) => {
    stdout += chunk.toString();
  });
  child.stderr.on("data", (chunk: Buffer) => {
    stderr += chunk.toString();
  });
  const [code] = (await once(child, "close")) as [number | null];
  return { code, stdout, stderr };
};

describe("the precompress step", () => {
  it("compresses the directory it is given and reports how many siblings it wrote", async () => {
    const root = await mkdtemp(join(tmpdir(), "precompress-main-"));
    await writeFile(join(root, "app.js"), "export const value = 1;\n".repeat(40));

    const { code, stdout } = await run([root]);
    await rm(root, { recursive: true, force: true });

    expect(code).toBe(0);
    expect(JSON.parse(stdout)).toEqual({ level: "info", event: "precompressed", siblings: 2 });
  }, 20_000);

  it("refuses to run without a directory, saying how to call it", async () => {
    const { code, stderr } = await run([]);

    expect(code).toBe(2);
    expect(stderr).toBe("Usage: node src/precompress-main.ts <the built app's directory>\n");
  }, 20_000);
});
