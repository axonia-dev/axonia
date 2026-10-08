import type { HealthSnapshot } from "@/features/health/domain/health";
import { formatShare } from "@/features/health/domain/health";
import { plural } from "@/lib/plural";

/**
 * Below this many members a share is false precision: one person is worth more than a fifth of it,
 * so "100%" describes three people rather than a healthy community. The same honesty rule that makes
 * cross-team reach read "unavailable" rather than a misleading zero.
 */
const SHARE_NEEDS_AT_LEAST = 6;

export function participationRead(
  participation: HealthSnapshot["participation"],
): { value: string; showTrend: boolean } {
  const { activeMembers, totalMembers, share } = participation;
  if (totalMembers < SHARE_NEEDS_AT_LEAST) {
    return {
      value: `${activeMembers} of ${plural(totalMembers, "member")}`,
      showTrend: false,
    };
  }
  return { value: formatShare(share), showTrend: true };
}
