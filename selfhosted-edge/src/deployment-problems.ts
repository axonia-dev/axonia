import { statSync } from "node:fs";
import { isAbsolute, join } from "node:path";

const LOCAL_HOSTNAMES: ReadonlySet<string> = new Set(["localhost", "127.0.0.1", "[::1]"]);
const HEADER_SAFE = /^[\x21-\x7e]+$/;

const notAnOrigin = (name: string, example: string): string =>
  `${name} must be an origin with no path, such as ${example}.`;
const STATIC_DIR_PROBLEM =
  "STATIC_DIR must be an absolute path to the built app, holding its index.html.";

// Exactly an origin: written the way URL writes one back, so nothing a cookie, a redirect or the
// backend's own PUBLIC_URL is compared against can differ by a path, a port spelling or a case.
const originOf = (raw: string): URL | null => {
  if (!URL.canParse(raw)) {
    return null;
  }
  const url = new URL(raw);
  return raw.replace(/\/$/, "") === url.origin ? url : null;
};

export const publicUrlProblem = (raw: string): string | null => {
  const url = originOf(raw);
  if (url === null) {
    return notAnOrigin("PUBLIC_URL", "https://axonia.example.com");
  }
  return url.protocol === "https:" || LOCAL_HOSTNAMES.has(url.hostname)
    ? null
    : "PUBLIC_URL must be https, or http on localhost: browsers refuse the session cookie anywhere else.";
};

export const backendOriginProblem = (raw: string): string | null =>
  originOf(raw) === null ? notAnOrigin("BACKEND_ORIGIN", "http://axonia-backend:8080") : null;

export const edgeSecretProblem = (raw: string): string | null =>
  HEADER_SAFE.test(raw)
    ? null
    : "EDGE_SHARED_SECRET must be printable ASCII with no spaces: it travels in a header.";

export const staticDirProblem = (raw: string): string | null => {
  if (!isAbsolute(raw)) {
    return STATIC_DIR_PROBLEM;
  }
  try {
    return statSync(join(raw, "index.html")).isFile() ? null : STATIC_DIR_PROBLEM;
  } catch {
    return STATIC_DIR_PROBLEM;
  }
};
