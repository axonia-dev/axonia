import { Bookmark } from "lucide-react";
import { useState } from "react";
import { Button } from "react-aria-components";

import { EmptyState } from "@/components/patterns/EmptyState";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { useMySaved } from "@/features/bookmarks/api/use-save";
import type { SavedItem } from "@/features/bookmarks/domain/saved";
import {
  ALL_COMMUNITIES,
  ALL_TYPES,
  filterSaved,
  type SavedKind,
  type SavedSort,
  scopeToCommunity,
  sortSaved,
  toReviewCount,
} from "@/features/bookmarks/domain/saved-filter";
import { SavedArtifactRow } from "@/features/bookmarks/ui/SavedArtifactRow";
import { SavedSessionRow } from "@/features/bookmarks/ui/SavedSessionRow";
import { SavedToolbar } from "@/features/bookmarks/ui/SavedToolbar";
import { usePageCommunity } from "@/features/community/ui/use-page-community";
import { useUsers } from "@/features/identity/api/use-users";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import type { ArtifactType } from "@/features/knowledge/domain/artifact-type";
import { failedToLoad } from "@/lib/failed-to-load";

const PAGE_SIZE = 25;

function typesById(types: ArtifactType[] | undefined) {
  return new Map((types ?? []).map((type) => [type.id, type]));
}

function distinctCommunities(items: ReadonlyArray<SavedItem>) {
  const byId = new Map<number, { id: number; label: string }>();
  for (const item of items) {
    if (item.community) {
      byId.set(item.community.id, {
        id: item.community.id,
        label: item.community.name,
      });
    }
  }
  return [...byId.values()];
}

/** Facilitator ids across every session save, resolved in one batched lookup
 * rather than one `useUsers` call per row. */
function distinctFacilitatorIds(items: ReadonlyArray<SavedItem>): string[] {
  const ids = items
    .filter((item) => item.kind === "session")
    .map((item) => item.session.facilitatorUserId)
    .filter((id): id is string => id !== null);
  return [...new Set(ids)];
}

interface SavedResultsProps {
  isLoading: boolean;
  hasItems: boolean;
  scoped: boolean;
  visible: ReadonlyArray<SavedItem>;
  windowed: ReadonlyArray<SavedItem>;
  typeById: Map<number, ArtifactType>;
  facilitatorNameOf: (userId: string | null) => string | undefined;
  onClearFilters: () => void;
  onShowMore: () => void;
}

/** "Nothing saved" reads differently once the page already implies a community: saved items
 * may still exist elsewhere, so the message must not claim there are none at all. */
function nothingSavedCopy(scoped: boolean) {
  return scoped
    ? {
        title: "Nothing saved in this community yet",
        description:
          "Tap the bookmark on any talk, RFC, recording, or session in this community to keep it here for later.",
      }
    : {
        title: "Nothing saved yet",
        description:
          "Tap the bookmark on any talk, RFC, recording, or session to keep it here for later.",
      };
}

function SavedResults({
  isLoading,
  hasItems,
  scoped,
  visible,
  windowed,
  typeById,
  facilitatorNameOf,
  onClearFilters,
  onShowMore,
}: Readonly<SavedResultsProps>) {
  if (isLoading) {
    return (
      <p className="text-muted-foreground text-center text-sm">Loading…</p>
    );
  }

  if (!hasItems) {
    const copy = nothingSavedCopy(scoped);
    return <EmptyState title={copy.title} description={copy.description} />;
  }

  if (visible.length === 0) {
    return (
      <EmptyState
        title="No saved items match these filters"
        description="Try a different search or type, or clear the filters to see everything."
        action={
          <Button
            onPress={onClearFilters}
            className="text-primary text-sm font-semibold underline-offset-2 hover:underline"
          >
            Clear filters
          </Button>
        }
      />
    );
  }

  return (
    <>
      <ul className="flex flex-col gap-3">
        {windowed.map((item) =>
          item.kind === "artifact" ? (
            <SavedArtifactRow
              key={`a-${item.artifact.id}`}
              item={item}
              type={typeById.get(item.artifact.typeId)}
              showCommunity={!scoped}
            />
          ) : (
            <SavedSessionRow
              key={`s-${item.session.id}`}
              item={item}
              facilitatorName={facilitatorNameOf(
                item.session.facilitatorUserId,
              )}
              showCommunity={!scoped}
            />
          ),
        )}
      </ul>
      {visible.length > windowed.length && (
        <div className="flex flex-col items-center gap-1">
          <Button
            onPress={onShowMore}
            className="border-border bg-surface hover:border-border-strong rounded-md border px-5 py-2.5 text-sm font-semibold"
          >
            Show more
          </Button>
          <span className="text-muted-foreground text-xs">
            Showing {windowed.length} of {visible.length}
          </span>
        </div>
      )}
    </>
  );
}

function useScopedSaved() {
  const saved = useMySaved();
  const page = usePageCommunity();
  const all: SavedItem[] = saved.data ?? [];
  return {
    items: page.scoped ? scopeToCommunity(all, page.community?.id) : all,
    isLoading: saved.isLoading || page.isLoading,
    scoped: page.scoped,
    saved,
  };
}

export function MySavedView() {
  const { items, isLoading, scoped, saved } = useScopedSaved();
  const typeById = typesById(useArtifactTypes().data);
  const users = useUsers(distinctFacilitatorIds(items)).data;
  const facilitatorNameOf = (userId: string | null) =>
    userId ? users?.[userId]?.displayName : undefined;

  const [query, setQuery] = useState("");
  const [communityId, setCommunityId] = useState(ALL_COMMUNITIES);
  const [kind, setKind] = useState<SavedKind>("all");
  const [typeId, setTypeId] = useState(ALL_TYPES);
  const [sort, setSort] = useState<SavedSort>("recent");
  const [reviewOnly, setReviewOnly] = useState(false);
  const [shown, setShown] = useState(PAGE_SIZE);

  const filterKey = `${query}|${communityId}|${kind}|${typeId}|${sort}|${reviewOnly}`;
  const [pageKey, setPageKey] = useState(filterKey);
  // Reset the visible window to the first page whenever the filters change.
  // This is the "adjust state during render" pattern (React docs: "You Might Not
  // Need an Effect") - intentionally NOT a useEffect, which would reintroduce a
  // react-hooks/set-state-in-effect violation and an extra render round-trip.
  if (filterKey !== pageKey) {
    setPageKey(filterKey);
    setShown(PAGE_SIZE);
  }

  function clearFilters() {
    setQuery("");
    setCommunityId(ALL_COMMUNITIES);
    setKind("all");
    setTypeId(ALL_TYPES);
    setReviewOnly(false);
  }

  const visible = sortSaved(
    filterSaved(items, { query, communityId, kind, typeId, reviewOnly }),
    sort,
  );
  const windowed = visible.slice(0, shown);
  const reviewCount = toReviewCount(items);

  return (
    <section className="flex flex-col gap-6">
      <header className="flex items-center gap-2">
        <Bookmark className="text-secondary size-5" />
        <h1 className="text-xl font-bold">Saved</h1>
        {items.length > 0 && (
          <span className="text-muted-foreground text-sm font-semibold">
            {items.length} saved
            {reviewCount > 0 && (
              <span className="text-primary"> · {reviewCount} to review</span>
            )}
          </span>
        )}
      </header>

      {items.length > 0 && (
        <SavedToolbar
          query={query}
          onQuery={setQuery}
          communities={distinctCommunities(items)}
          communityId={communityId}
          onCommunity={setCommunityId}
          kind={kind}
          onKind={(next) => {
            setKind(next);
            // "To review" is an artifact-only filter; leaving it on while
            // switching to Sessions would silently empty the tab.
            if (next === "session") {
              setReviewOnly(false);
            }
          }}
          typeId={typeId}
          onType={setTypeId}
          sort={sort}
          onSort={setSort}
          reviewOnly={reviewOnly}
          onReviewToggle={() => setReviewOnly((v) => !v)}
        />
      )}

      {failedToLoad(saved) ? (
        <LoadFailed
          inline
          message="Could not load the saved items."
          query={saved}
        />
      ) : (
        <SavedResults
          isLoading={isLoading}
          hasItems={items.length > 0}
          scoped={scoped}
          visible={visible}
          windowed={windowed}
          typeById={typeById}
          facilitatorNameOf={facilitatorNameOf}
          onClearFilters={clearFilters}
          onShowMore={() => setShown((n) => n + PAGE_SIZE)}
        />
      )}
    </section>
  );
}
