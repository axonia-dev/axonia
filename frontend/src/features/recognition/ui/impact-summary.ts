import type {
  ContributionType,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";
import {
  CONTRIBUTION_META,
  CONTRIBUTION_ORDER,
} from "@/features/recognition/ui/contribution-meta";

interface ImpactSummary {
  counts: Record<ContributionType, number>;
  communityCount: number;
  total: number;
  standing: string;
}

function typeClause(counts: Record<ContributionType, number>): string {
  const parts: string[] = [];
  for (const type of CONTRIBUTION_ORDER) {
    const n = counts[type];
    if (n === 0) continue;
    const meta = CONTRIBUTION_META[type];
    parts.push(`${n} ${n === 1 ? meta.noun : meta.nounPlural}`);
  }
  return parts.join(" and ");
}

/** A community's own page is already that one community, so it names no count of them. */
function communityClause(communityCount: number, scoped: boolean): string {
  if (scoped) return "";
  if (communityCount === 1) return " in one community";
  return ` across ${communityCount} communities`;
}

export function summarizeImpact(
  items: RecognizedContribution[],
  { scoped = false }: Readonly<{ scoped?: boolean }> = {},
): ImpactSummary {
  const counts: Record<ContributionType, number> = {
    SLOT_PRESENTED: 0,
    ARTIFACT_CAPTURED: 0,
  };
  const communities = new Set<number>();
  for (const item of items) {
    counts[item.contributionType] += 1;
    communities.add(item.communityId);
  }
  const total = items.length;
  const standing =
    total === 0
      ? "Contributions appear here as people appreciate them."
      : `Contributed ${typeClause(counts)}, appreciated by peers${communityClause(communities.size, scoped)}.`;

  return { counts, communityCount: communities.size, total, standing };
}
