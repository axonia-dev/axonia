import { z } from "zod";

import { type AgentScopeCode, knownScopes } from "./personal-access-token";

const AUTHORIZE_PARAMS = [
  "response_type",
  "client_id",
  "redirect_uri",
  "code_challenge",
  "code_challenge_method",
  "resource",
  "scope",
  "state",
] as const;
type AuthorizeParam = (typeof AUTHORIZE_PARAMS)[number];

/** The authorize request exactly as the MCP client sent it, for the backend to validate. */
export type AuthorizationRequest = Partial<Record<AuthorizeParam, string>>;

/** A parameter sent more than once is left out, so the backend refuses it as missing (RFC 6749 section 3.1). */
export function authorizationRequestFrom(
  search: URLSearchParams,
): AuthorizationRequest {
  const present = AUTHORIZE_PARAMS.flatMap((param) => {
    const [value, ...repeats] = search.getAll(param);
    return value !== undefined && repeats.length === 0
      ? [[param, value] as const]
      : [];
  });
  return Object.fromEntries(present);
}

function urlOf(value: string | undefined): URL | null {
  if (value === undefined) return null;
  try {
    return new URL(value);
  } catch {
    return null;
  }
}

export function returnHost(request: AuthorizationRequest): string | null {
  return urlOf(request.redirect_uri)?.hostname ?? null;
}

/** The host an https client_id names, before the backend has checked it: a hint, never a verified name. */
export function claimedClientHost(
  request: AuthorizationRequest,
): string | null {
  const url = urlOf(request.client_id);
  return url?.protocol === "https:" ? url.hostname : null;
}

export function initialScopes(requested: string[]): AgentScopeCode[] {
  const known = knownScopes(requested);
  return known.length > 0 ? known : ["read"];
}

const refusalReasonSchema = z.enum([
  "UNTRUSTED_CLIENT",
  "REDIRECT_MISMATCH",
  "NO_AGENT_ACCESS",
]);
export type RefusalReason = z.infer<typeof refusalReasonSchema>;

const LOOPBACK_HOSTS: ReadonlySet<string> = new Set(["localhost", "127.0.0.1"]);

/** The server only ever answers with a registered loopback redirect; the page refuses to follow
 *  anything else, so a server defect can never navigate the browser off this machine. */
function isLoopbackRedirect(url: string): boolean {
  if (!URL.canParse(url)) return false;
  const parsed = new URL(url);
  return parsed.protocol === "http:" && LOOPBACK_HOSTS.has(parsed.hostname);
}

export const authorizationOutcomeSchema = z.discriminatedUnion("outcome", [
  z.object({
    outcome: z.literal("READY"),
    clientName: z.string(),
    clientHost: z.string(),
    requestedScopes: z.array(z.string()),
  }),
  z.object({
    outcome: z.literal("REDIRECT"),
    redirectUrl: z.string().refine(isLoopbackRedirect),
  }),
  z.object({ outcome: z.literal("REFUSED"), reason: refusalReasonSchema }),
]);
export type AuthorizationOutcome = z.infer<typeof authorizationOutcomeSchema>;

const REFUSALS: ReadonlyMap<RefusalReason, string> = new Map([
  ["UNTRUSTED_CLIENT", "This app is not on the list of trusted apps."],
  [
    "REDIRECT_MISMATCH",
    "This request would return to an address the app never registered.",
  ],
  [
    "NO_AGENT_ACCESS",
    "Agents are not available to this account in this organization.",
  ],
]);

export function refusalLine(reason: RefusalReason): string {
  return REFUSALS.get(reason) ?? "";
}
