import { ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { SHELF_LABELS } from "@/features/community/ui/lobby/lobby-copy";
import {
  LoadMoreFailed,
  mayLoadMore,
  type Paging,
  type Shelf,
  useShelfPages,
} from "@/features/community/ui/lobby/lobby-paging";
import {
  LobbyCard,
  LobbyCardSkeleton,
} from "@/features/community/ui/lobby/LobbyCard";
import { ShelfHeading } from "@/features/community/ui/lobby/LobbyGrid";
import { useLoadMore } from "@/features/community/ui/lobby/use-load-more";
import type { LobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

interface RowProps {
  list: string;
  headingId: string;
  label: string;
  items: readonly LobbyItem[];
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
  showRole: boolean;
  paging: Paging;
}

/** Below the first breakpoint a shelf is a strip that scrolls with snap and loads at its end. */
function ScrollingRow({
  headingId,
  label,
  items,
  faces,
  onOpenThreshold,
  showRole,
  paging,
  list,
}: Readonly<RowProps>) {
  const strip = useRef<HTMLUListElement>(null);
  const sentinel = useLoadMore<HTMLLIElement>({
    active: mayLoadMore(paging),
    onReach: paging.loadMore,
    root: strip,
    rootMargin: "0px 50%",
  });
  return (
    <>
      <ShelfHeading id={headingId} label={label} />
      <ul
        ref={strip}
        className="relative -mx-6 flex snap-x snap-mandatory scroll-px-6 [scrollbar-width:none] gap-3 overflow-x-auto overscroll-x-contain px-6 pt-1 pb-3 [&::-webkit-scrollbar]:hidden"
      >
        {items.map((item, index) => (
          <li key={item.id} className="w-3/4 flex-none snap-start sm:w-2/5">
            <LobbyCard
              item={item}
              place={{ list, index }}
              faces={faces}
              onOpenThreshold={onOpenThreshold}
              showRole={showRole}
            />
          </li>
        ))}
        {mayLoadMore(paging) && (
          <li ref={sentinel} aria-hidden className="w-px flex-none" />
        )}
      </ul>
      <LoadMoreFailed paging={paging} />
    </>
  );
}

function usePagedWindow(count: number, perView: number, paging: Paging) {
  const [requested, setStart] = useState(0);
  // A shelf that shrank, after a join moved a community off it, keeps its window on cards.
  const start = paging.hasNextPage
    ? requested
    : Math.min(requested, Math.max(0, count - perView));
  const nearEnd = start + 2 * perView > count;
  const { loadMore } = paging;
  const ask = nearEnd && mayLoadMore(paging);

  useEffect(() => {
    if (ask) loadMore();
  }, [ask, loadMore]);

  return {
    start,
    canPrev: start > 0,
    canNext: start + perView < count || paging.hasNextPage,
    prev: () => setStart(Math.max(0, start - perView)),
    next: () => setStart(start + perView),
  };
}

/** From the first breakpoint up, a shelf shows a window of cards and pages with its arrows. */
function PagedRow(props: Readonly<RowProps & { perView: number }>) {
  const { headingId, label, items, faces, onOpenThreshold, showRole, perView } =
    props;
  const windowed = usePagedWindow(items.length, perView, props.paging);
  const visible = items.slice(windowed.start, windowed.start + perView);
  const waiting = Math.max(0, perView - visible.length);
  return (
    <>
      <ShelfHeading id={headingId} label={label}>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            aria-label="Previous communities"
            isDisabled={!windowed.canPrev}
            onPress={windowed.prev}
            className="size-8 rounded-full p-0"
          >
            <ChevronLeft size={16} aria-hidden />
          </Button>
          <Button
            variant="outline"
            size="sm"
            aria-label="More communities"
            isDisabled={!windowed.canNext}
            onPress={windowed.next}
            className="size-8 rounded-full p-0"
          >
            <ChevronRight size={16} aria-hidden />
          </Button>
        </div>
      </ShelfHeading>
      <ul
        className={cn(
          "grid gap-4",
          perView === 4 ? "grid-cols-4" : "grid-cols-3",
        )}
      >
        {visible.map((item, i) => (
          <li key={item.id}>
            <LobbyCard
              item={item}
              faces={faces}
              onOpenThreshold={onOpenThreshold}
              showRole={showRole}
              place={{ list: props.list, index: windowed.start + i }}
            />
          </li>
        ))}
        {props.paging.isFetchingNextPage &&
          Array.from({ length: waiting }, (_, i) => (
            <li key={`waiting-${i}`}>
              <LobbyCardSkeleton />
            </li>
          ))}
      </ul>
      <LoadMoreFailed paging={props.paging} />
    </>
  );
}

/** One labelled shelf: its first page came with the lobby, later pages are read as it is paged. */
export function LobbyShelfRow({
  shelf,
  faces,
  onOpenThreshold,
}: Readonly<{
  shelf: Shelf;
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
}>) {
  const id = useId();
  const wide = useMediaQuery("(min-width: 64rem)");
  const medium = useMediaQuery("(min-width: 48rem)");
  const pages = useShelfPages(shelf, faces);
  const props: RowProps = {
    list: shelf.key,
    headingId: id,
    label: SHELF_LABELS[shelf.key],
    items: pages.items,
    faces: pages.faces,
    onOpenThreshold,
    showRole: shelf.key === "JOINED",
    paging: pages.paging,
  };
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      {medium ? (
        <PagedRow {...props} perView={wide ? 4 : 3} />
      ) : (
        <ScrollingRow {...props} />
      )}
    </section>
  );
}
