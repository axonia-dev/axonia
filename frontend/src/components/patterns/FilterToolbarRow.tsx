import { Users } from "lucide-react";

import { SearchField } from "@/components/ui/SearchField";
import { Select } from "@/components/ui/Select";

interface CommunityOption {
  id: number;
  label: string;
}

interface SortOption<T extends string> {
  id: T;
  label: string;
}

interface FilterToolbarRowProps<T extends string> {
  query: string;
  onQuery: (v: string) => void;
  searchLabel: string;
  searchPlaceholder: string;
  communities: ReadonlyArray<CommunityOption>;
  communityId: number;
  onCommunity: (v: number) => void;
  /** Sentinel id the owning feature uses for "all communities" (its own ALL_COMMUNITIES). */
  allCommunitiesId: number;
  sort: T;
  sortOptions: ReadonlyArray<SortOption<T>>;
  onSort: (v: T) => void;
}

/**
 * The shared first row of a "my …" list toolbar: a full-width search field, an optional
 * community filter (shown only when the user spans more than one community), and a sort
 * select. Kept generic over the sort id so each feature keeps its own sort vocabulary.
 */
export function FilterToolbarRow<T extends string>({
  query,
  onQuery,
  searchLabel,
  searchPlaceholder,
  communities,
  communityId,
  onCommunity,
  allCommunitiesId,
  sort,
  sortOptions,
  onSort,
}: Readonly<FilterToolbarRowProps<T>>) {
  const communityOptions = [
    { id: allCommunitiesId, label: "All communities" },
    ...communities,
  ];
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      <SearchField
        label={searchLabel}
        value={query}
        onChange={onQuery}
        placeholder={searchPlaceholder}
        className="flex-1"
      />
      {communities.length > 1 && (
        <Select
          label="Community"
          hideLabel
          value={communityId}
          options={communityOptions}
          onChange={onCommunity}
          leadingIcon={<Users size={15} />}
          className="sm:w-52"
        />
      )}
      <Select
        label="Sort"
        hideLabel
        value={sort}
        options={sortOptions}
        onChange={onSort}
        className="sm:w-44"
      />
    </div>
  );
}
