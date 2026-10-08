import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import type { SelfView } from "@/features/expertise/domain/expertise";

export interface ShapeCommunity {
  id: number;
  slug: string;
  name: string;
}

function declaredCommunity(view: SelfView, id: number): ShapeCommunity | null {
  const declared = view.declarations.find((d) => d.communityId === id);
  if (declared === undefined) return null;
  return { id, slug: declared.communitySlug, name: declared.communityName };
}

/** An older self view carries no slug on an area's community; a declaration for the same id gives one instead. */
function lensCommunity(view: SelfView, id: number): ShapeCommunity | null {
  for (const area of view.areas) {
    const community = area.communities.find((c) => c.id === id);
    if (community === undefined) continue;
    if (community.slug === undefined) return declaredCommunity(view, id);
    return { id: community.id, slug: community.slug, name: community.name };
  }
  return declaredCommunity(view, id);
}

/** The lens chooses the shape's community; without one, the page's own community does. */
export function communityInScope(
  view: SelfView,
  scope: Readonly<{ lensId: number; page: ShapeCommunity | null }>,
): ShapeCommunity | null {
  if (scope.lensId !== ALL_COMMUNITIES)
    return lensCommunity(view, scope.lensId);
  return scope.page;
}
