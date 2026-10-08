import type { ErasureBlocker } from "@/features/privacy/domain/erasure";

/**
 * One sentence per blocker kind, in the ceiling. Split out of DeleteAccountDialog
 * so its own eslint `complexity` stays low: an early return per kind, not a
 * branching expression inline in the component.
 */
export function blockerMessage(blocker: ErasureBlocker): string {
  if (blocker.kind === "ENTERPRISE_MEMBERSHIP") {
    return "This account belongs to an organization, not to the person signed in. An administrator there has to remove it first.";
  }
  const community = blocker.communityName ?? "a community";
  return `This account is the sole owner of “${community}”. Transfer ownership or delete that community first.`;
}
