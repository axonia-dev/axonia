import { useState } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { KindBadge } from "@/components/patterns/KindBadge";
import { ListRow } from "@/components/patterns/ListRow";
import { Pagination } from "@/components/patterns/Pagination";
import { Avatar } from "@/components/ui/Avatar";
import { SearchField } from "@/components/ui/SearchField";
import { Skeleton } from "@/components/ui/Skeleton";
import { useCommunities } from "@/features/community/api/use-communities";
import { kindHue } from "@/lib/kind-hue";
import { plural } from "@/lib/plural";

const PAGE_SIZE = 20;

export function CommunityList() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useCommunities({
    page,
    size: PAGE_SIZE,
    q,
  });

  return (
    <section className="flex flex-col gap-4">
      <SearchField
        label="Filter communities"
        placeholder="Search communities"
        value={q}
        onChange={(value) => {
          setQ(value);
          setPage(0);
        }}
        className="max-w-xs"
      />

      {isLoading && (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      )}

      {isError && (
        <p role="alert" className="text-danger text-sm">
          Could not load communities.
        </p>
      )}

      {data && data.content.length === 0 && (
        <EmptyState title="No communities yet" />
      )}

      {data && data.content.length > 0 && (
        <ul className="flex flex-col gap-2">
          {data.content.map((community) => {
            const accent =
              community.accentColor ?? kindHue(String(community.kindId));
            return (
              <li key={community.id}>
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
                />
              </li>
            );
          })}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <Pagination
          page={data.number}
          totalPages={data.totalPages}
          onPageChange={setPage}
        />
      )}
    </section>
  );
}
