import type { MapContribution } from "@/features/expertise/domain/expertise";

/**
 * A face is a property of the contribution, never of the person: it is attached to one event on one date, the way a
 * byline is. It appears only where that event's reach earns it, and only a presented slot has a reach at all.
 */
const FACED_PERCENTILE = 0.75;

/**
 * A reach of zero is a measured absence of reach, not a missing measurement: the attendance reader answers zero for
 * every resolvable slot, so a community that never tracked attendance sends zeros rather than nulls. Counting them
 * would put the threshold at zero and a byline on every talk, which is a leaderboard, and the product draws none.
 */
function hasEarnedReach(
  contribution: MapContribution,
): contribution is MapContribution & { reach: number } {
  return (
    contribution.evidence.sourceType === "SLOT" &&
    contribution.reach !== null &&
    contribution.reach > 0
  );
}

function knownReaches(contributions: MapContribution[]): number[] {
  return contributions
    .filter(hasEarnedReach)
    .map((c) => c.reach)
    .sort((a, b) => a - b);
}

/**
 * A percentile of this community's own attendance, not a fixed headcount: a fixed number decides in advance that a
 * twelve-person guild never has a talk worth a byline. Null when nothing measurable exists, and then nothing is faced.
 */
export function reachThreshold(
  contributions: MapContribution[],
): number | null {
  const reaches = knownReaches(contributions);
  if (reaches.length === 0) return null;

  const index = Math.min(
    reaches.length - 1,
    Math.floor(reaches.length * FACED_PERCENTILE),
  );
  return reaches[index]!;
}

export function facedSignalIds(contributions: MapContribution[]): Set<number> {
  const threshold = reachThreshold(contributions);
  if (threshold === null) return new Set();

  return new Set(
    contributions
      .filter((c) => hasEarnedReach(c) && c.reach >= threshold)
      .map((c) => c.signalId),
  );
}
