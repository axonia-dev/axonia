import { readdir, readFile, writeFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { promisify } from "node:util";
import { brotliCompress, constants, gzip } from "node:zlib";

const brotli = promisify(brotliCompress);
const gzipped = promisify(gzip);

// The text formats static.ts serves; an image or a font is compressed already.
const COMPRESSIBLE: ReadonlySet<string> = new Set([
  ".html",
  ".js",
  ".mjs",
  ".css",
  ".json",
  ".map",
  ".webmanifest",
  ".txt",
  ".svg",
  ".ico",
]);

export interface Sibling {
  file: string;
  encoding: "br" | "gzip";
}

interface Encoder {
  encoding: Sibling["encoding"];
  suffix: string;
  encode: (bytes: Buffer) => Promise<Buffer>;
}

const ENCODERS: readonly Encoder[] = [
  {
    encoding: "br",
    suffix: ".br",
    encode: (bytes) =>
      brotli(bytes, {
        params: {
          [constants.BROTLI_PARAM_QUALITY]: constants.BROTLI_MAX_QUALITY,
          [constants.BROTLI_PARAM_SIZE_HINT]: bytes.length,
        },
      }),
  },
  {
    encoding: "gzip",
    suffix: ".gz",
    encode: (bytes) => gzipped(bytes, { level: constants.Z_BEST_COMPRESSION }),
  },
];

const compressibleFilesUnder = async (root: string): Promise<string[]> =>
  (await readdir(root, { withFileTypes: true, recursive: true }))
    .filter((entry) => entry.isFile() && COMPRESSIBLE.has(extname(entry.name).toLowerCase()))
    .map((entry) => join(entry.parentPath, entry.name))
    .sort();

/**
 * Writes a .br and a .gz sibling beside every compressible file of a build, each only when it is
 * smaller than the file itself: static.ts serves a sibling whenever one exists and the browser
 * takes its encoding.
 */
export async function precompress(root: string): Promise<Sibling[]> {
  const written: Sibling[] = [];
  for (const file of await compressibleFilesUnder(root)) {
    const bytes = await readFile(file);
    for (const { encoding, suffix, encode } of ENCODERS) {
      const compressed = await encode(bytes);
      if (compressed.length < bytes.length) {
        await writeFile(`${file}${suffix}`, compressed);
        written.push({ file: `${file}${suffix}`, encoding });
      }
    }
  }
  return written;
}
