import { readFile } from "node:fs/promises";
import { isHeaderSafe } from "./deployment-problems.ts";
import type { ClientAuthentication } from "./shared.ts";

export const JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

/** The kubelet rotates the projected token about hourly, so it is read fresh on every call. */
export const tokenFileAssertion =
  (path: string): ClientAuthentication =>
  async () => {
    const token = (await readFile(path, "utf8")).trim();
    if (token === "") {
      throw new Error(`The federated token file ${path} is empty.`);
    }
    return { client_assertion_type: JWT_BEARER, client_assertion: token };
  };

export interface ManagedIdentity {
  endpoint: string;
  header: string;
  clientId: string;
}

const TOKEN_EXCHANGE_RESOURCE = "api://AzureADTokenExchange";
const IDENTITY_API_VERSION = "2019-08-01";
// The identity provider's own timeout: a sign-in waits no longer on the platform than on Entra.
const IDENTITY_TIMEOUT_MS = 5000;
const REUSE_MARGIN_MS = 5 * 60_000;

interface ManagedIdentityRuntime {
  fetchFn?: typeof fetch;
  now?: () => number;
  timeoutMs?: number;
}

interface CachedToken {
  token: string;
  reuseUntil: number;
}

// Only the parse is caught: a body that could not be read fails with its own reason, a timeout or
// a dropped connection, rather than as an answer without a token.
const parsedObject = (text: string): Record<string, unknown> => {
  try {
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
};

/**
 * The token the platform's identity endpoint issues for the token-exchange audience. It is cached
 * until shortly before it expires and concurrent callers share one request, because every sign-in
 * asks. A failure is never cached. Messages name the HTTP status only: the identity header, the
 * token and the response body are secrets that must not reach the logs.
 */
export const managedIdentityAssertion = (
  identity: ManagedIdentity,
  { fetchFn = fetch, now = Date.now, timeoutMs = IDENTITY_TIMEOUT_MS }: ManagedIdentityRuntime = {},
): ClientAuthentication => {
  let cached: CachedToken | undefined;
  let inFlight: Promise<string> | undefined;

  const request = async (): Promise<string> => {
    // The HTTP client quotes a header value it refuses, which would put the secret in the logs.
    if (!isHeaderSafe(identity.header)) {
      throw new Error("The identity header is not printable ASCII with no spaces.");
    }
    const query = new URLSearchParams({
      resource: TOKEN_EXCHANGE_RESOURCE,
      "api-version": IDENTITY_API_VERSION,
      client_id: identity.clientId,
    });
    const response = await fetchFn(`${identity.endpoint}?${query.toString()}`, {
      method: "GET",
      headers: { "X-IDENTITY-HEADER": identity.header },
      redirect: "manual",
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (response.status < 200 || response.status > 299) {
      await response.body?.cancel();
      throw new Error(`The managed identity endpoint answered ${response.status}.`);
    }
    const { access_token: token, expires_on: expiresOn } = parsedObject(await response.text());
    if (typeof token !== "string" || token === "") {
      throw new Error(
        `The managed identity endpoint answered ${response.status} without an access token.`,
      );
    }
    const expiresAtMs = Number(expiresOn) * 1000;
    cached = Number.isFinite(expiresAtMs)
      ? { token, reuseUntil: expiresAtMs - REUSE_MARGIN_MS }
      : undefined;
    return token;
  };

  return async () => {
    if (cached !== undefined && now() < cached.reuseUntil) {
      return { client_assertion_type: JWT_BEARER, client_assertion: cached.token };
    }
    inFlight ??= request().finally(() => {
      inFlight = undefined;
    });
    return { client_assertion_type: JWT_BEARER, client_assertion: await inFlight };
  };
};
