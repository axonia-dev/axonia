import { Button } from "react-aria-components";

import { useIsDelegated } from "@/auth/delegated-session";
import { EmptyState } from "@/components/patterns/EmptyState";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { usePageCommunity } from "@/features/community/ui/use-page-community";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import {
  useDismissSignal,
  useHideTag,
  useUnhideTag,
} from "@/features/expertise/api/use-expertise-prefs";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import {
  ALL_COMMUNITIES,
  type AreaFilters,
  type AreaSort,
  declarationsInCommunity,
  filterAreas,
  filterDeclarations,
  sortAreas,
} from "@/features/expertise/domain/area-filter";
import {
  canDeclareFrom,
  declarationsIn,
  declaredOnly,
  hiddenTagCount,
} from "@/features/expertise/domain/declared-areas";
import type {
  EvidenceItem,
  SelfArea,
  SelfDeclaration,
  SelfView,
  Tier,
} from "@/features/expertise/domain/expertise";
import { DeclaredLane } from "@/features/expertise/ui/DeclaredLane";
import { ExpertiseHeader } from "@/features/expertise/ui/ExpertiseHeader";
import { ExpertiseToolbar } from "@/features/expertise/ui/ExpertiseToolbar";
import { ShapeSection } from "@/features/expertise/ui/ShapeSection";
import { TIER_ORDER } from "@/features/expertise/ui/tier-meta";
import { TierLane } from "@/features/expertise/ui/TierLane";
import { useAreaFilters } from "@/features/expertise/ui/use-area-filters";
import { VisibilityMenu } from "@/features/expertise/ui/VisibilityMenu";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

function groupByTier(areas: SelfArea[]): Record<Tier, SelfArea[]> {
  const groups: Record<Tier, SelfArea[]> = {
    PRACTITIONER: [],
    CONTRIBUTOR: [],
    INTERESTED: [],
    OPEN_TO_QUESTIONS: [],
  };
  for (const area of areas) {
    groups[area.tier].push(area);
  }
  return groups;
}

function distinctCommunities(view: SelfView, declarations: SelfDeclaration[]) {
  const byId = new Map<number, { id: number; label: string }>();
  for (const area of view.areas) {
    for (const c of area.communities) {
      byId.set(c.id, { id: c.id, label: c.name });
    }
  }
  for (const d of declarations) {
    byId.set(d.communityId, { id: d.communityId, label: d.communityName });
  }
  return [...byId.values()];
}

/** A community's page says nothing about the member's areas elsewhere, so it never claims there are none at all. */
function noAreasCopy(scoped: boolean) {
  return scoped
    ? {
        title: "No expertise areas in this community yet",
        description:
          "Areas appear after a talk, a contribution, an offered topic, or a declaration in this community.",
      }
    : {
        title: "No expertise areas yet",
        description:
          "Areas appear after a talk, a contribution, an offered topic, or a declaration in a community.",
      };
}

/** What stands in for the page while its self view is not there to show. */
function readNotice(
  read: Readonly<{ isLoading: boolean; failure: RetryableRead | undefined }>,
) {
  if (read.failure !== undefined) {
    return (
      <section className="flex flex-col gap-6">
        <ExpertiseHeader view={undefined} />
        <LoadFailed
          inline
          message="Could not load these expertise areas."
          query={read.failure}
        />
      </section>
    );
  }
  if (read.isLoading) {
    return (
      <p className="text-muted-foreground text-center text-sm">Loading…</p>
    );
  }
  return null;
}

/** The empty state only when there is nothing at all to show, and nowhere to add. */
function isBlank(view: SelfView, canDeclare: boolean): boolean {
  return (
    view.areas.length === 0 && !canDeclare && declaredOnly(view).length === 0
  );
}

function hasToolbarContent(
  view: SelfView | undefined,
  slug: string | undefined,
) {
  if (view === undefined) return false;
  return view.areas.length > 0 || declarationsIn(view, slug).length > 0;
}

/** What the page shows: the page's community scopes declarations, the toolbar narrows areas and declarations alike.
 *  A community's page reads that community alone, so it offers no community lens and its lens stays on all. */
function layoutOf(
  view: SelfView,
  slug: string | undefined,
  toolbar: Readonly<{ filters: AreaFilters; sort: AreaSort }>,
) {
  const lensId =
    slug === undefined ? toolbar.filters.communityId : ALL_COMMUNITIES;
  const filters = { ...toolbar.filters, communityId: lensId };
  const visible = sortAreas(filterAreas(view.areas, filters), toolbar.sort);
  const pageDeclarations = declarationsIn(view, slug);
  const declaredRows = filterDeclarations(declaredOnly(view, slug), filters);
  const hasContent = hasToolbarContent(view, slug);
  return {
    visible,
    byTier: groupByTier(visible),
    markedDeclarations: declarationsInCommunity(pageDeclarations, lensId),
    declaredRows,
    hasContent,
    lensOptions:
      slug === undefined ? distinctCommunities(view, pageDeclarations) : [],
    lensId,
    nothingMatches:
      hasContent && visible.length === 0 && declaredRows.length === 0,
  };
}

/** Inside a community the page reads that community's self view, and waits for the community itself. */
function useScopedSelfView() {
  const page = usePageCommunity();
  const selfView = useSelfView(page.slug);
  return {
    slug: page.slug,
    community: page.community ?? null,
    data: selfView.data,
    isLoading: selfView.isLoading || page.isLoading,
    failure: failedToLoad(selfView) ? selfView : undefined,
  };
}

export function MyExpertiseView() {
  const read = useScopedSelfView();
  const { slug, community, data } = read;
  const hideTag = useHideTag();
  const unhideTag = useUnhideTag();
  const dismiss = useDismissSignal();
  const removeDeclaration = useRemoveDeclaration();
  const delegated = useIsDelegated();

  const toolbar = useAreaFilters(hasToolbarContent(data, slug));

  const notice = readNotice(read);
  if (notice !== null) return notice;
  if (!data) return null;

  function handleDismiss(item: EvidenceItem, tag: string) {
    dismiss.mutate({
      sourceType: item.sourceType,
      sourceId: item.sourceId,
      tag,
    });
  }
  function handleRemoveDeclaration(tag: string) {
    if (slug === undefined) return;
    removeDeclaration.mutate({ slug, tag });
  }

  const { filters } = toolbar;
  const layout = layoutOf(data, slug, toolbar);
  const noAreas = noAreasCopy(slug !== undefined);
  const removeHandler =
    slug === undefined || delegated ? undefined : handleRemoveDeclaration;

  return (
    <section className="flex flex-col gap-6">
      <ExpertiseHeader
        view={data}
        action={
          <VisibilityMenu
            optedOut={data.optedOut}
            hiddenCount={hiddenTagCount(data)}
          />
        }
      />

      <ShapeSection view={data} page={community} lensId={layout.lensId} />

      {isBlank(
        data,
        !delegated && canDeclareFrom(data.declarableCommunities, slug),
      ) && (
        <EmptyState title={noAreas.title} description={noAreas.description} />
      )}

      {layout.hasContent && (
        <ExpertiseToolbar
          query={filters.query}
          onQuery={toolbar.setQuery}
          communities={layout.lensOptions}
          communityId={filters.communityId}
          onCommunity={toolbar.setCommunityId}
          sort={toolbar.sort}
          onSort={toolbar.setSort}
          hasTalks={filters.hasTalks}
          onHasTalksToggle={toolbar.toggleHasTalks}
          hasArtifacts={filters.hasArtifacts}
          onHasArtifactsToggle={toolbar.toggleHasArtifacts}
          hiddenOnly={filters.hiddenOnly}
          onHiddenOnlyToggle={toolbar.toggleHiddenOnly}
        />
      )}

      {layout.nothingMatches && (
        <EmptyState
          title="No areas match these filters"
          description="Try a different search or filter, or clear the filters to see everything."
          action={
            <Button
              onPress={toolbar.clear}
              className="text-primary text-sm font-semibold underline-offset-2 hover:underline"
            >
              Clear filters
            </Button>
          }
        />
      )}

      {layout.visible.length > 0 && (
        <div className="flex flex-col gap-5">
          {TIER_ORDER.map((tier) => (
            <TierLane
              key={`${tier}|${toolbar.key}`}
              tier={tier}
              areas={layout.byTier[tier]}
              defaultOpen={tier !== "INTERESTED"}
              onHide={(tag) => hideTag.mutate(tag)}
              onUnhide={(tag) => unhideTag.mutate(tag)}
              onDismiss={handleDismiss}
              slug={slug}
              declarations={layout.markedDeclarations}
              onRemoveDeclaration={removeHandler}
              isRemovingDeclaration={(tag) =>
                slug !== undefined &&
                removeDeclaration.isRemoving({ slug, tag })
              }
            />
          ))}
        </div>
      )}

      <DeclaredLane
        declarations={layout.declaredRows}
        slug={slug}
        communityId={layout.lensId}
        declarableCommunities={data.declarableCommunities}
      />
    </section>
  );
}
