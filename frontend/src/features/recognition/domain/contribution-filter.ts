import type { RecognizedContribution } from "@/features/recognition/domain/recognition";

export const ALL_COMMUNITIES = -1;

export type ContributionKind = "ALL" | "SLOT_PRESENTED" | "ARTIFACT_CAPTURED";

export type ContributionSort = "recent" | "oldest" | "loved" | "az";

export interface ContributionFilters {
  query: string;
  communityId: number;
  kind: ContributionKind;
  lovedOnly: boolean;
}

export function isLoved(item: RecognizedContribution): boolean {
  return item.loveCount > 0;
}

function matchesQuery(item: RecognizedContribution, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (q === "") {
    return true;
  }
  return [item.title, item.typeLabel ?? ""].join(" ").toLowerCase().includes(q);
}

function matchesCommunity(
  item: RecognizedContribution,
  communityId: number,
): boolean {
  return communityId === ALL_COMMUNITIES || item.communityId === communityId;
}

function matchesKind(
  item: RecognizedContribution,
  kind: ContributionKind,
): boolean {
  return kind === "ALL" || item.contributionType === kind;
}

export function filterContributions(
  items: ReadonlyArray<RecognizedContribution>,
  filters: ContributionFilters,
): RecognizedContribution[] {
  return items.filter(
    (item) =>
      matchesQuery(item, filters.query) &&
      matchesCommunity(item, filters.communityId) &&
      matchesKind(item, filters.kind) &&
      (!filters.lovedOnly || isLoved(item)),
  );
}

const comparators: Record<
  ContributionSort,
  (a: RecognizedContribution, b: RecognizedContribution) => number
> = {
  recent: (a, b) => b.occurredAt.localeCompare(a.occurredAt),
  oldest: (a, b) => a.occurredAt.localeCompare(b.occurredAt),
  loved: (a, b) => b.loveCount - a.loveCount,
  az: (a, b) => a.title.localeCompare(b.title),
};

/** Items belonging to one specific community: what a community-scoped page shows. */
export function scopeToCommunity(
  items: ReadonlyArray<RecognizedContribution>,
  communityId: number | undefined,
): RecognizedContribution[] {
  return communityId === undefined
    ? []
    : items.filter((item) => item.communityId === communityId);
}

export function sortContributions(
  items: ReadonlyArray<RecognizedContribution>,
  sort: ContributionSort,
): RecognizedContribution[] {
  return [...items].sort(comparators[sort]);
}
