import type {
  DeclarableCommunity,
  SelfArea,
  SelfDeclaration,
  SelfView,
  Tier,
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

const EVIDENCE_TIERS: readonly Tier[] = [
  "PRACTITIONER",
  "CONTRIBUTOR",
  "INTERESTED",
];

/** What others see of the member's declarations: every tag they can be asked about, once, under the strongest
 *  tier the member holds on any of them. Hidden declarations and an opted-out member show nothing. */
export function asOthersSeeIt(
  view: SelfView,
  declarations: ReadonlyArray<SelfDeclaration>,
): { tags: string[]; tier: Tier } {
  const tags = view.optedOut
    ? []
    : [...new Set(declarations.filter((d) => !d.hidden).map((d) => d.tag))];
  const held = view.areas.filter(
    (area) => !area.hidden && tags.includes(area.canonicalTag),
  );
  const tier =
    EVIDENCE_TIERS.find((candidate) =>
      held.some((area) => area.tier === candidate),
    ) ?? "OPEN_TO_QUESTIONS";
  return { tags, tier };
}
