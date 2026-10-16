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
import { ribbonSpan, ribbonWindow } from "@/features/events/domain/ribbon";
import { entryAtMs } from "@/features/events/ui/timeline/timeline";
import { TimeRibbon } from "@/features/events/ui/timeline/TimeRibbon";

type MemoriesQuery = Pick<
  ReturnType<typeof useMemories>,
  "data" | "hasNextPage" | "fetchNextPage"
>;

interface Props {
  slug: string;
  fromIso: string;
  toIso: string;
  nowMs: number;
  lensMs: number | null;
  /** Something is on the timeline already, so a ribbon is worth drawing even with no ticks. */
  hasContent: boolean;
  /** The oldest memory the list renders right now, or Infinity with none. */
  oldestShownMs: number;
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

function useScrub(
  memoriesQuery: MemoriesQuery,
  oldestShownMs: number,
  scrollToAt: (atMs: number) => void,
) {
  const generation = useRef(0);
  const pending = useRef<{ atMs: number; reach: number } | null>(null);
  const oldestShown = useRef(oldestShownMs);
  const query = useRef(memoriesQuery);

  useLayoutEffect(() => {
    query.current = memoriesQuery;
  });

  // A loaded page reaches the list a commit after its promise settles, so the scroll waits for it.
  useLayoutEffect(() => {
    oldestShown.current = oldestShownMs;
    const target = pending.current;
    if (target === null || oldestShownMs > target.reach) return;
    pending.current = null;
    scrollToAt(target.atMs);
  }, [oldestShownMs, scrollToAt]);

  return useCallback(
    async (atMs: number) => {
      generation.current += 1;
      const mine = generation.current;
      pending.current = null;
      const loaded = await loadBackTo(
        atMs,
        query.current,
        () => generation.current === mine,
      );
      if (loaded === null) return;
      if (!loaded.fetched || oldestShown.current <= loaded.oldestMs) {
        scrollToAt(atMs);
      } else {
        pending.current = { atMs, reach: loaded.oldestMs };
      }
    },
    [scrollToAt],
  );
}

/** The year the ribbon shows, its ticks, and the newest one once that year's ticks are in. */
function useRibbonYear(slug: string, fromIso: string, toIso: string) {
  const [yearsBack, setYearsBack] = useState(0);
  const range = ribbonWindow(fromIso, toIso, yearsBack);
  const ribbon = useRibbon(slug, range.fromIso, range.toIso);
  const ticks = ribbon.data?.ticks ?? [];
  const span = ribbonSpan(range, ribbon.data?.firstSessionAt ?? null, fromIso);
  const settled = ribbon.data !== undefined && !ribbon.isPlaceholderData;
  const newestAt = settled ? (ticks.at(-1)?.at ?? null) : undefined;
  return { yearsBack, setYearsBack, ticks, span, newestAt };
}

export function RibbonBar({
  slug,
  fromIso,
  toIso,
  nowMs,
  lensMs,
  hasContent,
  oldestShownMs,
  memoriesQuery,
  valueText,
  scrollToAt,
}: Readonly<Props>) {
  const { yearsBack, setYearsBack, ticks, span, newestAt } = useRibbonYear(
    slug,
    fromIso,
    toIso,
  );
  const scrub = useScrub(memoriesQuery, oldestShownMs, scrollToAt);
  const steppedBack = useRef<number | null>(null);

  // After "Further back", once that year's ticks are in: land on its newest session, if any.
  useEffect(() => {
    if (steppedBack.current !== yearsBack || newestAt === undefined) return;
    steppedBack.current = null;
    if (newestAt !== null) void scrub(new Date(newestAt).getTime());
  }, [yearsBack, newestAt, scrub]);

  const furtherBack = () => {
    steppedBack.current = yearsBack + 1;
    setYearsBack(yearsBack + 1);
  };
  const backToToday = () => {
    setYearsBack(0);
    void scrub(nowMs);
  };

  if (!hasContent && ticks.length === 0) return null;
  return (
    <TimeRibbon
      span={span}
      ticks={ticks}
      nowMs={nowMs}
      lensMs={lensMs ?? nowMs}
      valueText={valueText}
      onScrub={(atMs) => void scrub(atMs)}
      {...(span.hasOlder ? { onFurtherBack: furtherBack } : {})}
      {...(yearsBack > 0 ? { onBackToToday: backToToday } : {})}
    />
  );
}
