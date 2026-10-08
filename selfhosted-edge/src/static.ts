import { createReadStream, type Stats } from "node:fs";
import { realpath, stat } from "node:fs/promises";
import type { OutgoingHttpHeaders } from "node:http";
import { extname, join, sep } from "node:path";
import { pipeline } from "node:stream/promises";
import { type Exchange, sendProblem, writeHead } from "./respond.ts";

const SHELL = "index.html";
// Vite names every file it emits under assets/ by a hash of its content, so a changed file is a new
// name and the old name never needs revalidating.
const HASHED_ASSET = /^assets\/[^/]+-[A-Za-z0-9_-]{8,}\.[a-z0-9]+$/;
const IMMUTABLE = "public, max-age=31536000, immutable";
const REVALIDATE = "no-cache";
// The shell names the hashed files of one build. A cached shell would keep asking for files a newer
// build no longer has, so it is never stored.
const NEVER = "no-store";

const CONTENT_TYPES: Readonly<Record<string, string>> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".map": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
  ".woff": "font/woff",
};
const UNKNOWN_TYPE = "application/octet-stream";

// Best first. The image build writes both siblings beside every compressible file.
const ENCODINGS = [
  { name: "br", suffix: ".br" },
  { name: "gzip", suffix: ".gz" },
] as const;

interface Representation {
  path: string;
  real: string;
  stats: Stats;
  encoding: string | null;
}

// A segment that could name anything but a file in this directory is refused rather than cleaned:
// a separator or a NUL hidden behind percent-encoding, a dot segment URL parsing left encoded, an
// empty segment, or bytes that are not UTF-8.
const decodedSegment = (segment: string): string | null => {
  let decoded: string;
  try {
    decoded = decodeURIComponent(segment);
  } catch {
    return null;
  }
  const refused =
    decoded === "" ||
    decoded === "." ||
    decoded === ".." ||
    decoded.includes("/") ||
    decoded.includes("\\") ||
    decoded.includes("\u0000");
  return refused ? null : decoded;
};

export const relativeFile = (pathname: string): string | null => {
  if (pathname === "/") {
    return SHELL;
  }
  const segments = pathname.slice(1).split("/").map(decodedSegment);
  return segments.every((segment): segment is string => segment !== null)
    ? segments.join("/")
    : null;
};

interface Found {
  real: string;
  stats: Stats;
}

// The path is followed through every link to the file it names, which must lie inside the build:
// the segment checks keep a path's own text inside it, and this keeps a link from leading out. The
// file is then opened by that real path, so a link swapped in after the check is never followed. The
// root is a real path already (config.ts).
const fileAt = async (root: string, path: string): Promise<Found | null> => {
  try {
    const real = await realpath(path);
    const stats = real.startsWith(`${root}${sep}`) ? await stat(real) : null;
    return stats?.isFile() === true ? { real, stats } : null;
  } catch {
    return null;
  }
};

const located = async (root: string, relative: string): Promise<Representation | null> => {
  const path = join(root, relative);
  const found = await fileAt(root, path);
  return found === null ? null : { path, ...found, encoding: null };
};

const acceptedEncodings = (header: string | undefined): ReadonlySet<string> =>
  new Set(
    (header ?? "")
      .split(",")
      .map((part) => part.split(";"))
      .filter(([, ...parameters]) => !parameters.some((p) => /^\s*q=0(\.0{0,3})?\s*$/.test(p)))
      .map(([name]) => (name ?? "").trim().toLowerCase()),
  );

const preferred = async (exchange: Exchange, file: Representation): Promise<Representation> => {
  const accepted = acceptedEncodings(exchange.request.headers["accept-encoding"]);
  const root = exchange.configuration.staticRoot;
  for (const { name, suffix } of ENCODINGS) {
    const sibling = accepted.has(name) ? await fileAt(root, `${file.path}${suffix}`) : null;
    if (sibling !== null) {
      return { path: `${file.path}${suffix}`, ...sibling, encoding: name };
    }
  }
  return file;
};

const cacheControlFor = (relative: string): string => {
  if (relative === SHELL) {
    return NEVER;
  }
  return HASHED_ASSET.test(relative) ? IMMUTABLE : REVALIDATE;
};

const entityTag = ({ stats, encoding }: Representation): string =>
  `W/"${stats.size.toString(16)}-${Math.floor(stats.mtimeMs).toString(16)}${encoding === null ? "" : `-${encoding}`}"`;

const weakly = (tag: string): string => tag.trim().replace(/^W\//, "");

const isFresh = (ifNoneMatch: string | undefined, tag: string): boolean =>
  ifNoneMatch !== undefined &&
  (ifNoneMatch.trim() === "*" ||
    ifNoneMatch.split(",").some((candidate) => weakly(candidate) === weakly(tag)));

const headersFor = (relative: string, chosen: Representation): OutgoingHttpHeaders => {
  const cacheControl = cacheControlFor(relative);
  return {
    "content-type": CONTENT_TYPES[extname(relative).toLowerCase()] ?? UNKNOWN_TYPE,
    "cache-control": cacheControl,
    "x-content-type-options": "nosniff",
    vary: "Accept-Encoding",
    ...(cacheControl === NEVER ? {} : { etag: entityTag(chosen) }),
    ...(chosen.encoding === null ? {} : { "content-encoding": chosen.encoding }),
  };
};

const sendFile = async (
  exchange: Exchange,
  relative: string,
  file: Representation,
): Promise<void> => {
  const chosen = await preferred(exchange, file);
  const headers = headersFor(relative, chosen);
  if (
    typeof headers.etag === "string" &&
    isFresh(exchange.request.headers["if-none-match"], headers.etag)
  ) {
    writeHead(exchange, 304, headers);
    exchange.response.end();
    return;
  }
  writeHead(exchange, 200, { ...headers, "content-length": chosen.stats.size });
  if (exchange.request.method === "HEAD") {
    exchange.response.end();
    return;
  }
  await pipeline(createReadStream(chosen.real), exchange.response);
};

/** Serves the build's file at the request's path, or answers false when the build has none. */
export const serveStatic = async (exchange: Exchange): Promise<boolean> => {
  const relative = relativeFile(exchange.url.pathname);
  const file =
    relative === null ? null : await located(exchange.configuration.staticRoot, relative);
  if (relative === null || file === null) {
    return false;
  }
  await sendFile(exchange, relative, file);
  return true;
};

/** The app shell, for a navigation to a route the app draws: the reload of a deep link. */
export const sendShell = async (exchange: Exchange): Promise<void> => {
  const shell = await located(exchange.configuration.staticRoot, SHELL);
  if (shell === null) {
    sendProblem(exchange, {
      status: 404,
      title: "Not Found",
      detail: "Nothing is served at this address.",
    });
    return;
  }
  await sendFile(exchange, SHELL, shell);
};
