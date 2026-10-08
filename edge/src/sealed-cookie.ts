// Sealing is what lets this edge hold a session without holding a session store. The refresh
// token travels inside the cookie rather than beside it in a store of its own, so there is
// no per-session record to provision, expire, replicate or pay for, and no consistency model to
// reason about when two tabs renew at once: whatever the browser sends back is, by construction,
// the last thing this edge wrote.
//
// AES-GCM rather than a signature, because the payload is a live credential and not a claim about
// one. Signing would keep the browser from EDITING the refresh token while leaving it perfectly
// readable, which is most of what this whole change exists to prevent. GCM is authenticated
// encryption, so the same operation buys confidentiality and tamper-evidence together, and a
// cookie that has been altered by a byte fails to open rather than opening into something else.

const NONCE_BYTES = 12;

// A nonce repeated under one AES-GCM key is the failure mode that breaks the cipher outright, so
// it is drawn fresh per seal from the CSPRNG and never derived from anything about the session. At
// 96 bits the birthday bound is far beyond the number of seals a session cookie can ever produce.
// Spelled with its buffer type rather than left bare. The bare form widens to ArrayBufferLike,
// which Node's stricter WebCrypto signatures reject - and this module is also compiled by the Vite
// dev servers that run it in Node.
const randomNonce = (): Uint8Array<ArrayBuffer> =>
  crypto.getRandomValues(new Uint8Array(NONCE_BYTES));

// base64url, because a cookie value may not contain the padding or the two non-alphanumeric
// characters standard base64 emits, and percent-encoding the difference would be one more layer to
// get wrong in both directions.
const toBase64Url = (bytes: Uint8Array<ArrayBuffer>): string => {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
};

const fromBase64Url = (value: string): Uint8Array<ArrayBuffer> => {
  const binary = atob(value.replaceAll("-", "+").replaceAll("_", "/"));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
};

/**
 * The AES-256-GCM key, imported from the deployment's secret. The secret is 32 random bytes in
 * base64url; anything else is a deployment that cannot seal, which is a startup-shaped failure
 * rather than a per-request one and is reported as such by the caller.
 */
export async function sealingKey(secret: string): Promise<CryptoKey> {
  // Malformed and wrong-length are reported as one thing on purpose. An operator reading the edge's
  // log needs to be told what to generate, and "not correctly encoded" - which is what atob raises
  // on its own - sends them looking for a corrupted value rather than a wrong-sized one.
  const raw = ((): Uint8Array<ArrayBuffer> | null => {
    try {
      return fromBase64Url(secret);
    } catch {
      return null;
    }
  })();
  if (raw === null || raw.byteLength !== 32) {
    throw new Error("SESSION_COOKIE_KEY must be 32 bytes, base64url-encoded.");
  }
  return crypto.subtle.importKey("raw", raw, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

/** Seals a value into a cookie-safe string: base64url(nonce ‖ ciphertext ‖ tag). */
export async function seal(value: unknown, key: CryptoKey): Promise<string> {
  const nonce = randomNonce();
  const plaintext = new TextEncoder().encode(JSON.stringify(value));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, key, plaintext),
  );
  const sealed = new Uint8Array(nonce.byteLength + ciphertext.byteLength);
  sealed.set(nonce);
  sealed.set(ciphertext, nonce.byteLength);
  return toBase64Url(sealed);
}

/**
 * Opens a sealed cookie, or answers `null` for every way it can fail to be one.
 *
 * Tampering, truncation, a value sealed under a rotated key, and a value that is not base64 at all
 * are deliberately one answer rather than four. The caller's only useful response to any of them is
 * to treat the request as unauthenticated and start a fresh login, and an edge that distinguished
 * them in its response would be telling an attacker which of their guesses got closer.
 */
export async function unseal<T>(sealed: string, key: CryptoKey): Promise<T | null> {
  try {
    const bytes = fromBase64Url(sealed);
    if (bytes.byteLength <= NONCE_BYTES) {
      return null;
    }
    const plaintext = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: bytes.subarray(0, NONCE_BYTES) },
      key,
      bytes.subarray(NONCE_BYTES),
    );
    return JSON.parse(new TextDecoder().decode(plaintext)) as T;
  } catch {
    return null;
  }
}

/**
 * One named cookie's value out of a request, or undefined.
 *
 * Split on ";" and then on the FIRST "=" only: a sealed value is base64url and so never contains
 * one, but the OAuth handoff cookie is read by the same helper and a future payload that did would
 * otherwise be silently truncated at its first separator.
 */
export function readCookie(request: Request, name: string): string | undefined {
  const header = request.headers.get("Cookie");
  if (header === null) {
    return undefined;
  }
  for (const part of header.split(";")) {
    const separator = part.indexOf("=");
    if (separator === -1) {
      continue;
    }
    if (part.slice(0, separator).trim() === name) {
      return part.slice(separator + 1).trim();
    }
  }
  return undefined;
}

/**
 * `__Host-` is a browser-enforced prefix rather than a naming convention: a cookie carrying it is
 * refused unless it is Secure, has Path=/, and names no Domain. That last one is what earns it
 * here. Without it, anything able to write a cookie on a sibling of the apex - the identity
 * provider's host, another application's, or a subdomain that does not exist yet - could set a
 * Domain-scoped cookie of the same name that this edge would read as its own session.
 *
 * It is a literal here rather than something a deployment configures, on purpose: `namePrefix`
 * below is the only part of the name AuthEnv can vary, so a deployment cannot configure this
 * prefix away even by mistake, whether that mistake is deliberate or a copy-pasted config file.
 */
const HOST_COOKIE_PREFIX = "__Host-";

/**
 * Every cookie name, built from the one configurable prefix, one per application signing in
 * on the same host. Deriving them all from one value rather than configuring them separately keeps
 * a deployment from ever setting one without the others.
 */
export const hostCookieName = (namePrefix: string, purpose: string): string =>
  `${HOST_COOKIE_PREFIX}${namePrefix}-${purpose}`;

export const sessionCookieName = (namePrefix: string): string =>
  hostCookieName(namePrefix, "session");

export const oauthCookieName = (namePrefix: string): string =>
  hostCookieName(namePrefix, "oauth");

interface CookieOptions {
  maxAgeSeconds: number;
  /** Left off for the session cookie, which must survive the top-level redirect back from Keycloak. */
  sameSite?: "Lax" | "Strict";
}

export const cookieHeader = (name: string, value: string, options: CookieOptions): string =>
  [
    `${name}=${value}`,
    "Path=/",
    "Secure",
    "HttpOnly",
    `SameSite=${options.sameSite ?? "Lax"}`,
    `Max-Age=${options.maxAgeSeconds}`,
  ].join("; ");

/**
 * Clearing is a set with a zero lifetime and an empty value, and it must repeat every attribute the
 * cookie was written with. A browser matches a deletion to an existing cookie by name, path and
 * domain together, so a clear that omits `Path=/` leaves the original sitting there and the member
 * stays signed in through what the UI told them was a sign-out.
 */
export const clearedCookie = (name: string): string => cookieHeader(name, "", { maxAgeSeconds: 0 });
