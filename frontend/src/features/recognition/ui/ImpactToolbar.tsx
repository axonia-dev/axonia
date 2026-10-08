import { Heart } from "lucide-react";

import { FilterToggleChip } from "@/components/patterns/FilterToggleChip";
import { FilterToolbarRow } from "@/components/patterns/FilterToolbarRow";
import {
  ALL_COMMUNITIES,
  type ContributionKind,
  type ContributionSort,
} from "@/features/recognition/domain/contribution-filter";
import { KindChips } from "@/features/recognition/ui/KindChips";
import { cn } from "@/lib/cn";

interface CommunityOption {
  id: number;
  label: string;
}

interface ImpactToolbarProps {
  query: string;
  onQuery: (v: string) => void;
  communities: ReadonlyArray<CommunityOption>;
  communityId: number;
  onCommunity: (v: number) => void;
  kind: ContributionKind;
  onKind: (v: ContributionKind) => void;
  sort: ContributionSort;
  onSort: (v: ContributionSort) => void;
  lovedOnly: boolean;
  onLovedToggle: () => void;
}

const SORT_OPTIONS: ReadonlyArray<{ id: ContributionSort; label: string }> = [
  { id: "recent", label: "Recently earned" },
  { id: "oldest", label: "Oldest first" },
  { id: "loved", label: "Most loved" },
  { id: "az", label: "A-Z" },
];

export function ImpactToolbar({
  query,
  onQuery,
  communities,
  communityId,
  onCommunity,
  kind,
  onKind,
  sort,
  onSort,
  lovedOnly,
  onLovedToggle,
}: Readonly<ImpactToolbarProps>) {
  return (
    <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-lg border p-3">
      <FilterToolbarRow
        query={query}
        onQuery={onQuery}
        searchLabel="Search contributions"
        searchPlaceholder="Search contributions…"
        communities={communities}
        communityId={communityId}
        onCommunity={onCommunity}
        allCommunitiesId={ALL_COMMUNITIES}
        sort={sort}
        sortOptions={SORT_OPTIONS}
        onSort={onSort}
      />
      <div className="flex flex-wrap items-center gap-2 sm:flex-nowrap sm:justify-between">
        <KindChips selected={kind} onSelect={onKind} />
        <FilterToggleChip
          active={lovedOnly}
          onToggle={onLovedToggle}
          icon={<Heart size={14} className={cn(lovedOnly && "fill-current")} />}
        >
          Loved
        </FilterToggleChip>
      </div>
    </div>
  );
}
