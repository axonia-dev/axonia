import { type ReactNode, useId } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { useLobbySearch } from "@/features/community/api/use-lobby";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { SHELF_LABELS } from "@/features/community/ui/lobby/lobby-copy";
import {
  LoadMoreFailed,
  mayLoadMore,
  type Paging,
  type Shelf,
  usePaging,
  useShelfPages,
} from "@/features/community/ui/lobby/lobby-paging";
import {
  LobbyCard,
  LobbyCardSkeleton,
} from "@/features/community/ui/lobby/LobbyCard";
import { useLoadMore } from "@/features/community/ui/lobby/use-load-more";
import {
  type LobbyFaces,
  useLobbyFaces,
} from "@/features/community/ui/lobby/use-lobby-faces";
import { failedToLoad } from "@/lib/failed-to-load";
import { useMediaQuery } from "@/lib/use-media-query";

const GRID = "grid grid-cols-1 gap-x-4 gap-y-6 sm:grid-cols-2 lg:grid-cols-4";

/** The one list a search shows, as the lobby's morph claims name it. */
export const SEARCH_LIST = "search";

export function ShelfHeading({
  id,
  label,
  children,
}: Readonly<{ id: string; label: string; children?: ReactNode }>) {
  return (
    <div className="flex items-center justify-between gap-4">
      <h2
        id={id}
        className="font-display text-foreground text-lg font-bold tracking-tight"
      >
        {label}
      </h2>
      {children}
    </div>
  );
}

export function CardSkeletons({
  count,
  className,
}: Readonly<{ count: number; className?: string }>) {
  return (
    <div data-testid="lobby-card-skeletons" className={className ?? GRID}>
      {Array.from({ length: count }, (_, i) => (
        <LobbyCardSkeleton key={i} />
      ))}
    </div>
  );
}

function useColumns(): number {
  const wide = useMediaQuery("(min-width: 64rem)");
  const small = useMediaQuery("(min-width: 40rem)");
  if (wide) return 4;
  return small ? 2 : 1;
}

function LobbyGrid({
  list,
  label,
  items,
  faces,
  onOpenThreshold,
  paging,
}: Readonly<{
  list: string;
  label: string;
  items: readonly LobbyItem[];
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
  paging: Paging;
}>) {
  const id = useId();
  const columns = useColumns();
  const sentinel = useLoadMore<HTMLDivElement>({
    active: mayLoadMore(paging),
    onReach: paging.loadMore,
  });
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4">
      <ShelfHeading id={id} label={label} />
      <ul className={GRID}>
        {items.map((item, i) => (
          <li key={item.id}>
            <LobbyCard
              item={item}
              faces={faces}
              onOpenThreshold={onOpenThreshold}
              showRole={item.role !== null}
              place={{ list, index: i }}
            />
          </li>
        ))}
      </ul>
      {paging.isFetchingNextPage && <CardSkeletons count={columns} />}
      <LoadMoreFailed paging={paging} />
      {mayLoadMore(paging) && (
        <div ref={sentinel} aria-hidden className="h-px" />
      )}
    </section>
  );
}

/** "Every other community", or the one "Explore" grid of a small organization: 20 at a time. */
export function LobbyShelfGrid({
  shelf,
  faces,
  onOpenThreshold,
}: Readonly<{
  shelf: Shelf;
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
}>) {
  const pages = useShelfPages(shelf, faces);
  return (
    <LobbyGrid
      list={shelf.key}
      label={SHELF_LABELS[shelf.key]}
      items={pages.items}
      faces={pages.faces}
      onOpenThreshold={onOpenThreshold}
      paging={pages.paging}
    />
  );
}

/** Every community matching the search, joined or not, as the same cards the shelves show. */
export function LobbySearchGrid({
  q,
  onOpenThreshold,
}: Readonly<{ q: string; onOpenThreshold: (item: LobbyItem) => void }>) {
  const query = useLobbySearch(q);
  const items = query.data?.pages.flatMap((page) => page.content) ?? [];
  const faces = useLobbyFaces(items);
  const paging = usePaging(query);

  if (failedToLoad(query)) {
    return (
      <LoadFailed
        inline
        message="Could not search communities."
        query={query}
      />
    );
  }
  if (query.data === undefined) {
    return <CardSkeletons count={4} />;
  }
  if (items.length === 0) {
    return <EmptyState title="No matches" />;
  }
  return (
    <LobbyGrid
      list={SEARCH_LIST}
      label="Matches"
      items={items}
      faces={faces}
      onOpenThreshold={onOpenThreshold}
      paging={paging}
    />
  );
}
