import { SearchField } from "@/components/ui/SearchField";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import type {
  DirectorySegment,
  RoleCounts,
} from "@/features/community/domain/member-directory";

interface MembersToolbarProps {
  counts: RoleCounts;
  segment: DirectorySegment;
  onSegmentChange: (segment: DirectorySegment) => void;
  query: string;
  onQueryChange: (query: string) => void;
}

export function MembersToolbar({
  counts,
  segment,
  onSegmentChange,
  query,
  onQueryChange,
}: Readonly<MembersToolbarProps>) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <SegmentedControl
        label="Filter members by role"
        hideLabel
        value={segment}
        onChange={(value) => onSegmentChange(value as DirectorySegment)}
        options={[
          { id: "all", label: `All · ${counts.all}` },
          { id: "organizers", label: `Organizers · ${counts.organizers}` },
          { id: "members", label: `Members · ${counts.members}` },
        ]}
      />
      <SearchField
        label="Search members"
        value={query}
        onChange={onQueryChange}
        placeholder="Search by name or team…"
        className="min-w-[180px] flex-1"
      />
    </div>
  );
}
