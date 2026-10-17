import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";

import type { useMemories } from "@/features/events/api/use-memories";
import { useRibbon } from "@/features/events/api/use-ribbon";
import type { MemoryPage } from "@/features/events/domain/memory";
import type { RibbonSpan } from "@/features/events/domain/ribbon";
import {
  follow,
  ribbonBounds,
  ribbonYears,
  ticksIn,
  windowAround,
  windowAt,
} from "@/features/events/domain/ribbon";
import { entryAtMs } from "@/features/events/ui/timeline/timeline";
import { TimeRibbon } from "@/features/events/ui/timeline/TimeRibbon";

type MemoriesQuery = Pick<
  ReturnType<typeof useMemories>,
  "data" | "hasNextPage" | "fetchNextPage"
>;

interface Props {
  slug: string;
  /** Start of today UTC: the earliest the ribbon reaches when every session is still ahead. */
  todayIso: string;
  nowMs: number;
  lensMs: number | null;
  /** Something is on the timeline already, so a ribbon is worth drawing even with no ticks. */
  hasContent: boolean;
  /** The schedule and the first memories page have settled. A scrub before that would find no
   *  page to load from and be overruled by the opening position, so the ribbon waits for it. */
  ready: boolean;
  /** The oldest memory the list renders right now, or Infinity with none. */
  oldestShownMs: number;
  /** How far ahead the list's loaded rows reach, the last settled reach while it loads further. */
  reachedMs: number;
  /** Loads the list ahead until it holds `atMs`. */
  reachForward: (atMs: number) => void;
  memoriesQuery: MemoriesQuery;
  valueText: (atMs: number) => string;
  scrollToAt: (atMs: number) => void;
}

interface Loaded {
  /** The oldest memory the loaded pages hold once loading stopped. */
  oldestMs: number;
  fetched: boolean;
}

function oldestOf(pages: MemoryPage[] | undefined): number {
  const ats = (pages ?? []).flatMap((page) =>
    page.items.map((memory) => entryAtMs(memory.entry)),
  );
  return Math.min(Infinity, ...ats);
}

/**
 * Loads older memory pages until one reaches `atMs`, none is left, or a page fails; null once a
 * newer scrub took over. A page may hold no memory at all (every session in it skipped), so
 * progress is counted in pages, not in how old the oldest memory got. `cancelRefetch: false`
 * makes a scrub join the page already in flight instead of restarting it.
 */
async function loadBackTo(
  atMs: number,
  query: MemoriesQuery,
  current: () => boolean,
): Promise<Loaded | null> {
  let { hasNextPage } = query;
  let pages = query.data?.pages ?? [];
  let fetched = false;
  while (hasNextPage && oldestOf(pages) > atMs) {
    const result = await query.fetchNextPage({ cancelRefetch: false });
    if (!current()) return null;
    const next = result.data?.pages ?? [];
    if (result.isError || next.length <= pages.length) break;
    fetched = true;
    hasNextPage = result.hasNextPage;
    pages = next;
  }
  return { oldestMs: oldestOf(pages), fetched };
}

interface Reach {
  oldestShownMs: number;
  reachedMs: number;
}

/** The target a scrub waits for, and whether the list now holds it. */
interface Pending {
  atMs: number;
  landed: (reach: Reach) => boolean;
}

function useScrub(
  memoriesQuery: MemoriesQuery,
  reach: Reach,
  { reachForward, scrollToAt }: Pick<Props, "reachForward" | "scrollToAt">,
) {
  const generation = useRef(0);
  const pending = useRef<Pending | null>(null);
  const shown = useRef(reach);
  const query = useRef(memoriesQuery);
  const { oldestShownMs, reachedMs } = reach;

  useLayoutEffect(() => {
    query.current = memoriesQuery;
    shown.current = { oldestShownMs, reachedMs };
  });

  // A loaded page reaches the list a commit after it lands, so the scroll waits for it. A passive
  // effect: the timeline's layout effect first holds the reader's row still against a prepend by
  // setting `scrollTop`, which would cancel a smooth scroll already under way.
  useEffect(() => {
    const target = pending.current;
    if (target === null || !target.landed({ oldestShownMs, reachedMs })) {
      return;
    }
    pending.current = null;
    scrollToAt(target.atMs);
  }, [oldestShownMs, reachedMs, scrollToAt]);

  return useCallback(
    async (atMs: number) => {
      generation.current += 1;
      const mine = generation.current;
      pending.current = null;
      if (atMs > shown.current.reachedMs) {
        pending.current = {
          atMs,
          landed: (next) => next.reachedMs >= atMs,
        };
        reachForward(atMs);
        return;
      }
      const loaded = await loadBackTo(
        atMs,
        query.current,
        () => generation.current === mine,
      );
      if (loaded === null) return;
      if (!loaded.fetched || shown.current.oldestShownMs <= loaded.oldestMs) {
        scrollToAt(atMs);
      } else {
        pending.current = {
          atMs,
          landed: (next) => next.oldestShownMs <= loaded.oldestMs,
        };
      }
    },
    [reachForward, scrollToAt],
  );
}

/**
 * The window the ribbon shows: centred on now until the reader moves it, rolled along by a lens
 * nearing an edge, and left alone while a pointer holds the ribbon so it never shifts under it.
 */
function useRollingWindow(
  bounds: RibbonSpan,
  lensMs: number | null,
  nowMs: number,
) {
  const [startMs, setStartMs] = useState<number | null>(null);
  const [held, setHeld] = useState(false);
  const [lastLens, setLastLens] = useState(lensMs);
  const span =
    startMs === null ? windowAround(nowMs, bounds) : windowAt(startMs, bounds);

  if (lensMs !== lastLens) {
    setLastLens(lensMs);
    if (!held && lensMs !== null && lastLens !== null) {
      const next = follow(span, lensMs, lensMs - lastLens, bounds);
      if (next.fromMs !== span.fromMs) setStartMs(next.fromMs);
    }
  }

  const slide = (deltaMs: number) => {
    setStartMs((start) => {
      const from = start ?? windowAround(nowMs, bounds).fromMs;
      return windowAt(from + deltaMs, bounds).fromMs;
    });
  };
  return { span, slide, setHeld, recentre: () => setStartMs(null) };
}

export function RibbonBar({
  slug,
  todayIso,
  nowMs,
  lensMs,
  hasContent,
  ready,
  oldestShownMs,
  reachedMs,
  reachForward,
  memoriesQuery,
  valueText,
  scrollToAt,
}: Readonly<Props>) {
  const [firstSessionAt, setFirstSessionAt] = useState<string | null>(null);
  const bounds = ribbonBounds(firstSessionAt, new Date(todayIso).getTime());
  const { span, slide, setHeld, recentre } = useRollingWindow(
    bounds,
    lensMs,
    nowMs,
  );
  const ribbon = useRibbon(slug, ribbonYears(span));
  if (
    ribbon.firstSessionAt !== undefined &&
    ribbon.firstSessionAt !== firstSessionAt
  ) {
    setFirstSessionAt(ribbon.firstSessionAt);
  }
  const scrub = useScrub(
    memoriesQuery,
    { oldestShownMs, reachedMs },
    { reachForward, scrollToAt },
  );

  const backToToday = () => {
    recentre();
    void scrub(nowMs);
  };

  const empty = !hasContent && ribbon.ticks.length === 0;
  if (!ready || empty) return null;
  return (
    <TimeRibbon
      span={span}
      bounds={bounds}
      ticks={ticksIn(ribbon.ticks, bounds)}
      nowMs={nowMs}
      lensMs={lensMs ?? nowMs}
      valueText={valueText}
      onScrub={(atMs) => void scrub(atMs)}
      onSlide={slide}
      onHold={setHeld}
      onBackToToday={backToToday}
    />
  );
}
