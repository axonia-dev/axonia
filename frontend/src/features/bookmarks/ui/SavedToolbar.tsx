import { FilterToggleChip } from "@/components/patterns/FilterToggleChip";
import { FilterToolbarRow } from "@/components/patterns/FilterToolbarRow";
import {
  ALL_COMMUNITIES,
  type SavedKind,
  type SavedSort,
} from "@/features/bookmarks/domain/saved-filter";
import { KindSegment } from "@/features/bookmarks/ui/KindSegment";
import { TypeChips } from "@/features/knowledge/ui/TypeChips";

interface CommunityOption {
  id: number;
  label: string;
}

interface SavedToolbarProps {
  query: string;
  onQuery: (v: string) => void;
  communities: ReadonlyArray<CommunityOption>;
  communityId: number;
  onCommunity: (v: number) => void;
  kind: SavedKind;
  onKind: (v: SavedKind) => void;
  typeId: number;
  onType: (v: number) => void;
  sort: SavedSort;
  onSort: (v: SavedSort) => void;
  reviewOnly: boolean;
  onReviewToggle: () => void;
}

const SORT_OPTIONS: ReadonlyArray<{ id: SavedSort; label: string }> = [
  { id: "recent", label: "Recently saved" },
  { id: "oldest", label: "Oldest first" },
  { id: "loved", label: "Most loved" },
  { id: "az", label: "A-Z" },
];

export function SavedToolbar({
  query,
  onQuery,
  communities,
  communityId,
  onCommunity,
  kind,
  onKind,
  typeId,
  onType,
  sort,
  onSort,
  reviewOnly,
  onReviewToggle,
}: Readonly<SavedToolbarProps>) {
  return (
    <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-lg border p-3">
      <FilterToolbarRow
        query={query}
        onQuery={onQuery}
        searchLabel="Search saved items"
        searchPlaceholder="Search saved items…"
        communities={communities}
        communityId={communityId}
        onCommunity={onCommunity}
        allCommunitiesId={ALL_COMMUNITIES}
        sort={sort}
        sortOptions={SORT_OPTIONS}
        onSort={onSort}
      />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <KindSegment value={kind} onChange={onKind} />
        {kind !== "session" && (
          <FilterToggleChip active={reviewOnly} onToggle={onReviewToggle}>
            To review
          </FilterToggleChip>
        )}
      </div>
      {kind !== "session" && (
        <>
          <hr className="border-border" />
          <TypeChips selected={typeId} onSelect={onType} />
        </>
      )}
    </div>
  );
}
