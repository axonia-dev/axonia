// Reading claims off a token the app was handed, never verifying one. The signature is checked by
// the backend on every request; nothing rendered from these claims grants anything, it only decides
// what to draw, so a token this cannot parse degrades to "no claims" rather than to an error.

interface JwtClaims {
  [claim: string]: unknown;
}

function base64UrlDecode(segment: string): string {
  const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
  // atob wants a length that is a multiple of four, and base64url drops the padding that would
  // give it one.
  const paddingNeeded = (4 - (normalized.length % 4)) % 4;
  return atob(normalized + "=".repeat(paddingNeeded));
}

/**
 * The claims of a JWT's payload, or `undefined` for anything that is not one: no dot-separated
 * payload segment, a segment that is not base64url, or a payload that is not a JSON object.
 */
export function decodeJwtPayload(token: string): JwtClaims | undefined {
  const [, payloadSegment] = token.split(".");
  if (payloadSegment === undefined) return undefined;
  try {
    const parsed: unknown = JSON.parse(base64UrlDecode(payloadSegment));
    if (parsed !== null && typeof parsed === "object") {
      return parsed as JwtClaims;
    }
    return undefined;
  } catch {
    return undefined;
  }
}
