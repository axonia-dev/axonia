import { z } from "zod";

// SOLE_OWNER names the community; ENTERPRISE_MEMBERSHIP has none to name (the
// blocker is the enterprise membership itself, not any one community), so the
// backend genuinely sends null there rather than omitting the field.
const erasureBlockerSchema = z.object({
  kind: z.enum(["ENTERPRISE_MEMBERSHIP", "SOLE_OWNER"]),
  organizationId: z.string(),
  communityName: z.string().nullable(),
});
export type ErasureBlocker = z.infer<typeof erasureBlockerSchema>;

export const erasurePreflightSchema = z.object({
  permitted: z.boolean(),
  blockers: erasureBlockerSchema.array(),
});
export type ErasurePreflight = z.infer<typeof erasurePreflightSchema>;

export interface PrivacyGateway {
  /** Whether the caller's account may be erased right now, and why not if it can't. */
  erasurePreflight(): Promise<ErasurePreflight>;
  /** Erases the caller's own account. The subject comes from the token; there is
   *  nothing to pass. The backend re-checks the same guards at this point, so a
   *  permitted preflight is not a guarantee this still succeeds. */
  eraseAccount(): Promise<void>;
}
