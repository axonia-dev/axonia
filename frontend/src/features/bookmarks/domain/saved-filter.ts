import type { SavedItem } from "@/features/bookmarks/domain/saved";
import { ALL_TYPES } from "@/features/knowledge/ui/TypeChips";

export { ALL_TYPES };
export const ALL_COMMUNITIES = -1;

export type SavedSort = "recent" | "oldest" | "loved" | "az";
export type SavedKind = "all" | "artifact" | "session";

export interface SavedFilters {
  query: string;
  communityId: number;
  kind: SavedKind;
  typeId: number;
  reviewOnly: boolean;
}

export function savedItemTitle(item: SavedItem): string {
  return item.kind === "artifact"
    ? item.artifact.title
    : (item.session.title ?? "Session");
}

export function isToReview(item: SavedItem): boolean {
  return item.kind === "artifact" && item.helped !== true;
}

export function toReviewCount(items: ReadonlyArray<SavedItem>): number {
  return items.filter(isToReview).length;
}

function matchesKind(item: SavedItem, kind: SavedKind): boolean {
  return kind === "all" || item.kind === kind;
}

function matchesType(item: SavedItem, typeId: number): boolean {
  if (typeId === ALL_TYPES) {
    return true;
  }
  return item.kind !== "artifact" || item.artifact.typeId === typeId;
}

function matchesCommunity(item: SavedItem, communityId: number): boolean {
  return communityId === ALL_COMMUNITIES || item.community?.id === communityId;
}

function matchesQuery(item: SavedItem, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (q === "") {
    return true;
  }
  if (item.kind === "artifact") {
    const a = item.artifact;
    return [a.title, a.description, ...a.tags].some((s) =>
      s.toLowerCase().includes(q),
    );
  }
  return savedItemTitle(item).toLowerCase().includes(q);
}

export function filterSaved(
  items: ReadonlyArray<SavedItem>,
  f: SavedFilters,
): SavedItem[] {
  return items.filter(
    (i) =>
      matchesKind(i, f.kind) &&
      matchesType(i, f.typeId) &&
      matchesCommunity(i, f.communityId) &&
      matchesQuery(i, f.query) &&
      (!f.reviewOnly || isToReview(i)),
  );
}

/** Items belonging to one specific community: what a community-scoped page shows. */
export function scopeToCommunity(
  items: ReadonlyArray<SavedItem>,
  communityId: number | undefined,
): SavedItem[] {
  return communityId === undefined
    ? []
    : items.filter((item) => item.community?.id === communityId);
}

function loveOf(item: SavedItem): number {
  return item.kind === "artifact" ? item.artifact.loveCount : 0;
}

const comparators: Record<SavedSort, (a: SavedItem, b: SavedItem) => number> = {
  recent: (a, b) => b.savedAt.localeCompare(a.savedAt),
  oldest: (a, b) => a.savedAt.localeCompare(b.savedAt),
  loved: (a, b) => loveOf(b) - loveOf(a),
  az: (a, b) => savedItemTitle(a).localeCompare(savedItemTitle(b)),
};

export function sortSaved(
  items: ReadonlyArray<SavedItem>,
  sort: SavedSort,
): SavedItem[] {
  return [...items].sort(comparators[sort]);
}
