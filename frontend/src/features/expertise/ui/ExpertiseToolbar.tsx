import { EyeOff, FileText, Presentation } from "lucide-react";

import { FilterToggleChip } from "@/components/patterns/FilterToggleChip";
import { FilterToolbarRow } from "@/components/patterns/FilterToolbarRow";
import {
  ALL_COMMUNITIES,
  type AreaSort,
} from "@/features/expertise/domain/area-filter";

interface CommunityOption {
  id: number;
  label: string;
}

interface ExpertiseToolbarProps {
  query: string;
  onQuery: (v: string) => void;
  communities: ReadonlyArray<CommunityOption>;
  communityId: number;
  onCommunity: (v: number) => void;
  sort: AreaSort;
  onSort: (v: AreaSort) => void;
  hasTalks: boolean;
  onHasTalksToggle: () => void;
  hasArtifacts: boolean;
  onHasArtifactsToggle: () => void;
  hiddenOnly: boolean;
  onHiddenOnlyToggle: () => void;
}

const SORT_OPTIONS: ReadonlyArray<{ id: AreaSort; label: string }> = [
  { id: "depth", label: "Depth" },
  { id: "recent", label: "Most recent" },
  { id: "az", label: "A-Z" },
];

export function ExpertiseToolbar({
  query,
  onQuery,
  communities,
  communityId,
  onCommunity,
  sort,
  onSort,
  hasTalks,
  onHasTalksToggle,
  hasArtifacts,
  onHasArtifactsToggle,
  hiddenOnly,
  onHiddenOnlyToggle,
}: Readonly<ExpertiseToolbarProps>) {
  return (
    <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-lg border p-3">
      <FilterToolbarRow
        query={query}
        onQuery={onQuery}
        searchLabel="Search expertise areas"
        searchPlaceholder="Search expertise areas…"
        communities={communities}
        communityId={communityId}
        onCommunity={onCommunity}
        allCommunitiesId={ALL_COMMUNITIES}
        sort={sort}
        sortOptions={SORT_OPTIONS}
        onSort={onSort}
      />
      <div className="flex flex-wrap items-center gap-2">
        <FilterToggleChip
          active={hasTalks}
          onToggle={onHasTalksToggle}
          icon={<Presentation size={14} />}
        >
          Has talks
        </FilterToggleChip>
        <FilterToggleChip
          active={hasArtifacts}
          onToggle={onHasArtifactsToggle}
          icon={<FileText size={14} />}
        >
          Has artifacts
        </FilterToggleChip>
        <FilterToggleChip
          active={hiddenOnly}
          onToggle={onHiddenOnlyToggle}
          icon={<EyeOff size={14} />}
        >
          Hidden only
        </FilterToggleChip>
      </div>
    </div>
  );
}
