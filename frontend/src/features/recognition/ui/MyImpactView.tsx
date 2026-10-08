import { useState } from "react";
import { Button } from "react-aria-components";

import { EmptyState } from "@/components/patterns/EmptyState";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { usePageCommunity } from "@/features/community/ui/use-page-community";
import { useMyContributions } from "@/features/recognition/api/use-recognition";
import {
  ALL_COMMUNITIES,
  type ContributionKind,
  type ContributionSort,
  filterContributions,
  scopeToCommunity,
  sortContributions,
} from "@/features/recognition/domain/contribution-filter";
import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { ContributionCard } from "@/features/recognition/ui/ContributionCard";
import { ImpactHeader } from "@/features/recognition/ui/ImpactHeader";
import { ImpactToolbar } from "@/features/recognition/ui/ImpactToolbar";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

const PAGE_SIZE = 25;

function distinctCommunities(items: ReadonlyArray<RecognizedContribution>) {
  const byId = new Map<number, { id: number; label: string }>();
  for (const item of items) {
    if (item.communityName !== null) {
      byId.set(item.communityId, {
        id: item.communityId,
        label: item.communityName,
      });
    }
  }
  return [...byId.values()];
}

interface ImpactResultsProps {
  isLoading: boolean;
  failure: RetryableRead | undefined;
  hasItems: boolean;
  scoped: boolean;
  visible: ReadonlyArray<RecognizedContribution>;
  windowed: ReadonlyArray<RecognizedContribution>;
  onClearFilters: () => void;
  onShowMore: () => void;
}

/** "No contributions" reads differently once the page already implies a community: contributions
 * may still exist elsewhere, so the message must not claim there are none at all. */
function noContributionsCopy(scoped: boolean) {
  return scoped
    ? {
        title: "No contributions in this community yet",
        description:
          "Talks and captured artifacts appear here as peers appreciate them in this community.",
      }
    : {
        title: "No contributions yet",
        description:
          "Talks and captured artifacts appear here as peers appreciate them.",
      };
}

function ImpactResults({
  isLoading,
  failure,
  hasItems,
  scoped,
  visible,
  windowed,
  onClearFilters,
  onShowMore,
}: Readonly<ImpactResultsProps>) {
  if (failure !== undefined) {
    return (
      <LoadFailed
        inline
        message="Could not load these contributions."
        query={failure}
      />
    );
  }

  if (isLoading) {
    return (
      <p className="text-muted-foreground text-center text-sm">Loading…</p>
    );
  }

  if (!hasItems) {
    const copy = noContributionsCopy(scoped);
    return <EmptyState title={copy.title} description={copy.description} />;
  }

  if (visible.length === 0) {
    return (
      <EmptyState
        title="No contributions match these filters"
        description="Try a different search or filter, or clear the filters to see everything."
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
        {windowed.map((c) => (
          <ContributionCard
            key={`${c.contributionType}-${c.contributionId}`}
            contribution={c}
          />
        ))}
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

function useScopedContributions() {
  const contributions = useMyContributions();
  const page = usePageCommunity();
  const all = contributions.data ?? [];
  return {
    items: page.scoped ? scopeToCommunity(all, page.community?.id) : all,
    isLoading: contributions.isLoading || page.isLoading,
    failure: failedToLoad(contributions) ? contributions : undefined,
    scoped: page.scoped,
  };
}

export function MyImpactView() {
  const { items, isLoading, failure, scoped } = useScopedContributions();

  const [query, setQuery] = useState("");
  const [communityId, setCommunityId] = useState(ALL_COMMUNITIES);
  const [kind, setKind] = useState<ContributionKind>("ALL");
  const [sort, setSort] = useState<ContributionSort>("recent");
  const [lovedOnly, setLovedOnly] = useState(false);
  const [shown, setShown] = useState(PAGE_SIZE);

  const filterKey = `${query}|${communityId}|${kind}|${sort}|${lovedOnly}`;
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
    setKind("ALL");
    setLovedOnly(false);
  }

  const visible = sortContributions(
    filterContributions(items, { query, communityId, kind, lovedOnly }),
    sort,
  );
  const windowed = visible.slice(0, shown);

  return (
    <section className="flex flex-col gap-6">
      <ImpactHeader
        items={failure === undefined ? items : undefined}
        scoped={scoped}
      />

      {items.length > 0 && (
        <ImpactToolbar
          query={query}
          onQuery={setQuery}
          communities={distinctCommunities(items)}
          communityId={communityId}
          onCommunity={setCommunityId}
          kind={kind}
          onKind={setKind}
          sort={sort}
          onSort={setSort}
          lovedOnly={lovedOnly}
          onLovedToggle={() => setLovedOnly((v) => !v)}
        />
      )}

      <ImpactResults
        isLoading={isLoading}
        failure={failure}
        hasItems={items.length > 0}
        scoped={scoped}
        visible={visible}
        windowed={windowed}
        onClearFilters={clearFilters}
        onShowMore={() => setShown((n) => n + PAGE_SIZE)}
      />
    </section>
  );
}
