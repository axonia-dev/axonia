import { useEffect, useRef, useState } from "react";

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
  memoriesQuery: MemoriesQuery;
  valueText: (atMs: number) => string;
  scrollToAt: (atMs: number) => void;
}

function oldestOf(pages: MemoryPage[] | undefined): number {
  const ats = (pages ?? []).flatMap((page) =>
    page.items.map((memory) => entryAtMs(memory.entry)),
  );
  return Math.min(Infinity, ...ats);
}

const nextFrame = () =>
  new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

/** Loads older memory pages until one reaches `atMs` or none is left; false once superseded. */
async function loadBackTo(
  atMs: number,
  query: MemoriesQuery,
  current: () => boolean,
): Promise<boolean> {
  let hasNextPage = query.hasNextPage;
  let oldest = oldestOf(query.data?.pages);
  while (hasNextPage && oldest > atMs) {
    const result = await query.fetchNextPage();
    if (!current()) return false;
    const reached = oldestOf(result.data?.pages);
    if (result.isError || reached >= oldest) return true;
    hasNextPage = result.hasNextPage;
    oldest = reached;
  }
  return true;
}

export function RibbonBar({
  slug,
  fromIso,
  toIso,
  nowMs,
  lensMs,
  hasContent,
  memoriesQuery,
  valueText,
  scrollToAt,
}: Readonly<Props>) {
  const [yearsBack, setYearsBack] = useState(0);
  const range = ribbonWindow(fromIso, toIso, yearsBack);
  const ribbon = useRibbon(slug, range.fromIso, range.toIso);
  const ticks = ribbon.data?.ticks ?? [];
  const span = ribbonSpan(range, ribbon.data?.firstSessionAt ?? null, fromIso);
  const generation = useRef(0);
  const steppedBack = useRef<number | null>(null);

  async function scrub(atMs: number) {
    generation.current += 1;
    const mine = generation.current;
    const fetched = memoriesQuery.hasNextPage;
    const current = () => generation.current === mine;
    if (!(await loadBackTo(atMs, memoriesQuery, current))) return;
    if (fetched) {
      await nextFrame();
      if (!current()) return;
    }
    scrollToAt(atMs);
  }

  const newest = ticks.at(-1);
  useEffect(() => {
    if (steppedBack.current !== yearsBack || newest === undefined) return;
    steppedBack.current = null;
    void scrub(new Date(newest.at).getTime());
  });

  if (!hasContent && ticks.length === 0) return null;
  return (
    <TimeRibbon
      span={span}
      ticks={ticks}
      nowMs={nowMs}
      lensMs={lensMs ?? nowMs}
      valueText={valueText}
      onScrub={(atMs) => void scrub(atMs)}
      {...(span.hasOlder
        ? {
            onFurtherBack: () => {
              steppedBack.current = yearsBack + 1;
              setYearsBack(yearsBack + 1);
            },
          }
        : {})}
      {...(yearsBack > 0
        ? {
            onBackToToday: () => {
              setYearsBack(0);
              void scrub(nowMs);
            },
          }
        : {})}
    />
  );
}
