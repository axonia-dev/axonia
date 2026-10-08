import type {
  SelfArea,
  SelfDeclaration,
} from "@/features/expertise/domain/expertise";

export const ALL_COMMUNITIES = -1;

export type AreaSort = "depth" | "recent" | "az";

export interface AreaFilters {
  query: string;
  communityId: number;
  hasTalks: boolean;
  hasArtifacts: boolean;
  hiddenOnly: boolean;
}

export function totalSignals(area: SelfArea): number {
  const e = area.evidence;
  return e.presentedCount + e.contributedCount + e.offeredCount;
}

function matchesQuery(tag: string, query: string): boolean {
  const q = query.trim().toLowerCase();
  return q === "" || tag.toLowerCase().includes(q);
}

function inCommunity(area: SelfArea, communityId: number): boolean {
  return (
    communityId === ALL_COMMUNITIES ||
    area.communities.some((c) => c.id === communityId)
  );
}

export function filterAreas(
  areas: ReadonlyArray<SelfArea>,
  filters: AreaFilters,
): SelfArea[] {
  return areas.filter(
    (area) =>
      matchesQuery(area.canonicalTag, filters.query) &&
      inCommunity(area, filters.communityId) &&
      (!filters.hasTalks || area.evidence.presentedCount > 0) &&
      (!filters.hasArtifacts || area.evidence.contributedCount > 0) &&
      (!filters.hiddenOnly || area.hidden),
  );
}

export function declarationsInCommunity(
  declarations: ReadonlyArray<SelfDeclaration>,
  communityId: number,
): SelfDeclaration[] {
  return declarations.filter(
    (d) => communityId === ALL_COMMUNITIES || d.communityId === communityId,
  );
}

/** Filters a declaration as an area with no evidence: it never has talks or artifacts. */
export function filterDeclarations(
  declarations: ReadonlyArray<SelfDeclaration>,
  filters: AreaFilters,
): SelfDeclaration[] {
  if (filters.hasTalks || filters.hasArtifacts) return [];
  return declarationsInCommunity(declarations, filters.communityId).filter(
    (d) =>
      matchesQuery(d.tag, filters.query) && (!filters.hiddenOnly || d.hidden),
  );
}

function latestOccurredAt(area: SelfArea): string {
  let latest = "";
  for (const item of area.evidence.recent) {
    if (item.occurredAt > latest) {
      latest = item.occurredAt;
    }
  }
  return latest;
}

const comparators: Record<AreaSort, (a: SelfArea, b: SelfArea) => number> = {
  depth: (a, b) => totalSignals(b) - totalSignals(a),
  recent: (a, b) => latestOccurredAt(b).localeCompare(latestOccurredAt(a)),
  az: (a, b) => a.canonicalTag.localeCompare(b.canonicalTag),
};

export function sortAreas(
  areas: ReadonlyArray<SelfArea>,
  sort: AreaSort,
): SelfArea[] {
  return [...areas].sort(comparators[sort]);
}
