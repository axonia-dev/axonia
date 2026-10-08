import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { brotliDecompressSync, gunzipSync } from "node:zlib";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { precompress } from "./precompress.ts";

const SCRIPT = "export const greeting = 'hello';\n".repeat(40);

describe("precompress", () => {
  let root: string;

  beforeEach(async () => {
    root = await mkdtemp(join(tmpdir(), "precompress-"));
  });

  afterEach(async () => {
    await rm(root, { recursive: true, force: true });
  });

  it("writes a brotli and a gzip sibling that each decode to the file", async () => {
    await writeFile(join(root, "app.js"), SCRIPT);

    const written = await precompress(root);

    expect(written).toEqual([
      { file: join(root, "app.js.br"), encoding: "br" },
      { file: join(root, "app.js.gz"), encoding: "gzip" },
    ]);
    expect(brotliDecompressSync(await readFile(join(root, "app.js.br"))).toString()).toBe(SCRIPT);
    expect(gunzipSync(await readFile(join(root, "app.js.gz"))).toString()).toBe(SCRIPT);
  });

  it("walks every directory under the root, in a stable order", async () => {
    await mkdir(join(root, "assets", "fonts"), { recursive: true });
    await writeFile(join(root, "index.html"), `<!doctype html>${SCRIPT}`);
    await writeFile(join(root, "assets", "fonts", "icons.svg"), `<svg>${SCRIPT}</svg>`);

    const written = await precompress(root);

    expect(written.map((sibling) => sibling.file)).toEqual([
      join(root, "assets", "fonts", "icons.svg.br"),
      join(root, "assets", "fonts", "icons.svg.gz"),
      join(root, "index.html.br"),
      join(root, "index.html.gz"),
    ]);
  });

  it("leaves a format that is compressed already alone", async () => {
    await writeFile(join(root, "photo.png"), SCRIPT);

    expect(await precompress(root)).toEqual([]);
  });

  it("writes no sibling that is not smaller than the file", async () => {
    await writeFile(join(root, "tiny.css"), "a{}");

    expect(await precompress(root)).toEqual([]);
  });

  it("reads the extension whatever its case", async () => {
    await writeFile(join(root, "LEGACY.JS"), SCRIPT);

    expect((await precompress(root)).map((sibling) => sibling.encoding)).toEqual(["br", "gzip"]);
  });
});
