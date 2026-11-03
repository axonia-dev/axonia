import { readFile } from "node:fs/promises";
import type { ClientAuthentication } from "./shared.ts";

export const JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

const readUtf8 = (path: string): Promise<string> => readFile(path, "utf8");

/** The kubelet rotates the projected token about hourly, so it is read fresh on every call. */
export const tokenFileAssertion =
  (path: string, read: (path: string) => Promise<string> = readUtf8): ClientAuthentication =>
  async () => {
    const token = (await read(path)).trim();
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
const IDENTITY_TIMEOUT_MS = 10_000;
const REUSE_MARGIN_MS = 5 * 60_000;

interface CachedToken {
  token: string;
  reuseUntil: number;
}

/**
 * The token the platform's identity endpoint issues for the token-exchange audience. It is cached
 * until shortly before it expires and concurrent callers share one request, because every sign-in
 * asks. A failure is never cached. Messages name the HTTP status only: the identity header, the
 * token and the response body are secrets that must not reach the logs.
 */
export const managedIdentityAssertion = (
  identity: ManagedIdentity,
  fetchFn: typeof fetch = fetch,
  now: () => number = Date.now,
): ClientAuthentication => {
  let cached: CachedToken | undefined;
  let inFlight: Promise<string> | undefined;

  const request = async (): Promise<string> => {
    const query = new URLSearchParams({
      resource: TOKEN_EXCHANGE_RESOURCE,
      "api-version": IDENTITY_API_VERSION,
      client_id: identity.clientId,
    });
    const response = await fetchFn(`${identity.endpoint}?${query.toString()}`, {
      method: "GET",
      headers: { "X-IDENTITY-HEADER": identity.header },
      redirect: "manual",
      signal: AbortSignal.timeout(IDENTITY_TIMEOUT_MS),
    });
    if (response.status < 200 || response.status > 299) {
      throw new Error(`The managed identity endpoint answered ${response.status}.`);
    }
    const body: unknown = await response.json().catch(() => null);
    const { access_token: token, expires_on: expiresOn } = (body ?? {}) as Record<string, unknown>;
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
