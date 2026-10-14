import { type ReactNode, useId, useState } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { KindBadge } from "@/components/patterns/KindBadge";
import { ListRow } from "@/components/patterns/ListRow";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Pagination } from "@/components/patterns/Pagination";
import { Avatar } from "@/components/ui/Avatar";
import { SearchField } from "@/components/ui/SearchField";
import { Skeleton } from "@/components/ui/Skeleton";
import { useCommunities } from "@/features/community/api/use-communities";
import type { CommunityListing } from "@/features/community/domain/community";
import type { MembershipFilter } from "@/features/community/domain/community-gateway";
import { communityHue } from "@/features/community/ui/community-accent";
import { CommunityTile } from "@/features/community/ui/CommunityTile";
import { ExploreJoinButton } from "@/features/community/ui/ExploreJoinButton";
import { failedToLoad } from "@/lib/failed-to-load";
import { plural } from "@/lib/plural";

const PAGE_SIZE = 20;

function useSection(q: string, membership: MembershipFilter) {
  const [page, setPage] = useState(0);
  const [lastQ, setLastQ] = useState(q);
  if (lastQ !== q) {
    setLastQ(q);
    setPage(0);
  }
  const query = useCommunities({ page, size: PAGE_SIZE, q, membership });
  return { query, page, setPage };
}

type Section = ReturnType<typeof useSection>;

function SectionShell({
  label,
  count,
  children,
}: Readonly<{ label: string; count: number; children: ReactNode }>) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <h2 className="text-muted-foreground flex items-center gap-2 text-xs font-bold tracking-widest uppercase">
        <span id={id}>{label}</span>
        <span
          data-testid="section-count"
          className="text-foreground opacity-60"
        >
          {count}
        </span>
      </h2>
      {children}
    </section>
  );
}

function SectionPager({ section }: Readonly<{ section: Section }>) {
  const data = section.query.data;
  if (data === undefined || data.totalPages <= 1) {
    return null;
  }
  return (
    <Pagination
      page={data.number}
      totalPages={data.totalPages}
      onPageChange={section.setPage}
    />
  );
}

function ExploreRow({ community }: Readonly<{ community: CommunityListing }>) {
  const accent = communityHue(community);
  return (
    <ListRow
      href={`/communities/${community.slug}`}
      title={community.name}
      meta={plural(community.memberCount, "member")}
      accent={accent}
      leading={
        <Avatar
          name={community.name}
          hue={accent}
          src={community.badgeUrl ?? null}
        />
      }
      trailing={
        <KindBadge
          kindId={community.kindId}
          label={community.kind}
          accentColor={community.accentColor ?? null}
        />
      }
      action={<ExploreJoinButton slug={community.slug} />}
    />
  );
}

function ListSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}

function JoinedSection({ section }: Readonly<{ section: Section }>) {
  const rows = section.query.data?.content ?? [];
  return (
    <>
      {failedToLoad(section.query) && (
        <LoadFailed
          inline
          message="Could not load joined communities."
          query={section.query}
        />
      )}
      {rows.length > 0 && (
        <SectionShell
          label="Joined"
          count={section.query.data?.totalElements ?? rows.length}
        >
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {rows.map((community) => (
              <li key={community.id} className="flex">
                <CommunityTile community={community} />
              </li>
            ))}
          </ul>
          <SectionPager section={section} />
        </SectionShell>
      )}
    </>
  );
}

function ExploreSection({ section }: Readonly<{ section: Section }>) {
  const rows = section.query.data?.content ?? [];
  return (
    <>
      {failedToLoad(section.query) && (
        <LoadFailed
          inline
          message="Could not load communities to explore."
          query={section.query}
        />
      )}
      {rows.length > 0 && (
        <SectionShell
          label="Explore"
          count={section.query.data?.totalElements ?? rows.length}
        >
          <ul className="flex flex-col gap-2">
            {rows.map((community) => (
              <li key={community.id}>
                <ExploreRow community={community} />
              </li>
            ))}
          </ul>
          <SectionPager section={section} />
        </SectionShell>
      )}
    </>
  );
}

function isEmpty(section: Section): boolean {
  return section.query.data?.content.length === 0;
}

export function CommunityList() {
  const [q, setQ] = useState("");
  const joined = useSection(q, "JOINED");
  const explore = useSection(q, "EXPLORE");
  const loading = joined.query.isLoading || explore.query.isLoading;
  const nothing = isEmpty(joined) && isEmpty(explore);

  return (
    <section className="flex flex-col gap-6">
      <SearchField
        label="Filter communities"
        placeholder="Search communities"
        value={q}
        onChange={setQ}
        className="sm:max-w-xs"
      />
      {loading && <ListSkeleton />}
      {nothing && (
        <EmptyState
          title={q.trim() === "" ? "No communities yet" : "No matches"}
        />
      )}
      <JoinedSection section={joined} />
      <ExploreSection section={explore} />
    </section>
  );
}
