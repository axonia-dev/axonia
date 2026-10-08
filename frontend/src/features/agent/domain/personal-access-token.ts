import { z } from "zod";

import { optionalFutureExpirySchema } from "@/lib/future-expiry-schema";

/**
 * The agent scope catalog, mirroring the backend AgentScope enum. Reads are grouped; writes are
 * per-capability. The roster is its own read: reading a community's schedule and reading the people
 * in it are different decisions, so the label names what is handed over.
 */
export const AGENT_SCOPES = [
  { code: "read", label: "Read schedule, communities, and library" },
  { code: "members:read", label: "Read community member lists and learners" },
  { code: "library:contribute", label: "Contribute library content" },
  { code: "topic:propose", label: "Propose topics" },
  { code: "actionItem:raise", label: "Raise action items" },
  { code: "session:signup", label: "Sign up to present" },
  { code: "attendance:signal", label: "Signal attendance" },
  { code: "community:join", label: "Join communities" },
  { code: "session:attach", label: "Attach artifacts to sessions" },
  { code: "session:detach", label: "Remove artifacts from sessions" },
  { code: "schedule:manage", label: "Create and shape sessions" },
  { code: "learning:declare", label: "Declare areas to learn" },
] as const;

export type AgentScopeCode = (typeof AGENT_SCOPES)[number]["code"];

const personalTokenStatusSchema = z.enum(["ACTIVE", "REVOKED", "EXPIRED"]);
export type PersonalTokenStatus = z.infer<typeof personalTokenStatusSchema>;

export const personalTokenSchema = z.object({
  id: z.number(),
  name: z.string(),
  scopes: z.array(z.string()),
  lastUsedAt: z.string().nullish(),
  expiresAt: z.string().nullish(),
  createdAt: z.string(),
  status: personalTokenStatusSchema,
});
export type PersonalToken = z.infer<typeof personalTokenSchema>;

export const createTokenBodySchema = z.object({
  name: z.string().min(1, "Name is required"),
  scopes: z.array(z.string()).min(1, "Select at least one permission"),
  expiresAt: optionalFutureExpirySchema,
});
export type CreateTokenBody = z.infer<typeof createTokenBodySchema>;

export const createTokenResponseSchema = z.object({ token: z.string() });
export type CreateTokenResponse = z.infer<typeof createTokenResponseSchema>;

export function scopeLabel(code: string): string {
  return AGENT_SCOPES.find((scope) => scope.code === code)?.label ?? code;
}

/** Narrows a token's stored scope strings to the codes this UI can render, in catalog order. */
export function knownScopes(scopes: string[]): AgentScopeCode[] {
  return AGENT_SCOPES.filter((scope) => scopes.includes(scope.code)).map(
    (scope) => scope.code,
  );
}

/**
 * The complement of {@link knownScopes}: codes a token already carries that this catalog does
 * not recognise. An edit must carry these through unchanged, since the member was never shown
 * them and so never chose to remove them.
 */
export function unknownScopes(scopes: string[]): string[] {
  const known: readonly string[] = AGENT_SCOPES.map((scope) => scope.code);
  return scopes.filter((code) => !known.includes(code));
}

export const updateTokenBodySchema = createTokenBodySchema.omit({
  expiresAt: true,
});
export type UpdateTokenBody = z.infer<typeof updateTokenBodySchema>;
