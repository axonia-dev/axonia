import type {
  DeclarableCommunity,
  SelfArea,
  SelfDeclaration,
  SelfView,
} from "@/features/expertise/domain/expertise";

export function declarationsIn(
  view: SelfView,
  slug?: string,
): SelfDeclaration[] {
  return slug === undefined
    ? view.declarations
    : view.declarations.filter((d) => d.communitySlug === slug);
}

/** An area is declared when a declaration names it in a community its evidence comes from. */
export function isDeclared(
  area: SelfArea,
  declarations: SelfDeclaration[],
): boolean {
  return declarations.some(
    (d) =>
      d.tag === area.canonicalTag &&
      area.communities.some((c) => c.id === d.communityId),
  );
}

/** The declarations evidence does not already show: they get their own lane. */
export function declaredOnly(view: SelfView, slug?: string): SelfDeclaration[] {
  return declarationsIn(view, slug).filter(
    (d) => !view.areas.some((area) => isDeclared(area, [d])),
  );
}

/** Hidden tags, counted once each: hiding is per tag, whether evidence or only a declaration carries it. */
export function hiddenTagCount(view: SelfView): number {
  const tags = new Set<string>();
  for (const area of view.areas) {
    if (area.hidden) tags.add(area.canonicalTag);
  }
  for (const d of view.declarations) {
    if (d.hidden) tags.add(d.tag);
  }
  return tags.size;
}

/** A community's page adds only where the member can declare in it; the page across communities, in any such community. */
export function canDeclareFrom(
  declarable: ReadonlyArray<DeclarableCommunity>,
  slug: string | undefined,
): boolean {
  return slug === undefined
    ? declarable.length > 0
    : declarable.some((community) => community.slug === slug);
}
