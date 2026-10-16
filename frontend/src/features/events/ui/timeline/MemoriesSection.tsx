import { useEffect } from "react";

import { useLoadMoreOnView } from "@/components/patterns/use-load-more-on-view";
import type { Memory } from "@/features/events/domain/memory";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { buildMemoryTimeline } from "@/features/events/ui/timeline/memory-timeline";
import { MemoryRow } from "@/features/events/ui/timeline/MemoryRow";
import {
  entryKey,
  occurrenceHref,
} from "@/features/events/ui/timeline/timeline";
import { GapRow, MonthRow } from "@/features/events/ui/timeline/timeline-rows";

interface Props {
  memories: Memory[];
  arriving: ReadonlySet<number>;
  slug: string;
  todayIso: string;
  canManage: boolean;
  labelOf: (entry: ScheduleEntry) => string;
  /** Present while an older page is still there to load. */
  loadOlder: (() => void) | null;
  isError: boolean;
  scroller: () => HTMLElement | null;
}

export function MemoriesSection({
  memories,
  arriving,
  slug,
  todayIso,
  canManage,
  labelOf,
  loadOlder,
  isError,
  scroller,
}: Readonly<Props>) {
  const { rootRef, sentinelRef } = useLoadMoreOnView(loadOlder !== null, () =>
    loadOlder?.(),
  );
  useEffect(() => {
    const main = scroller();
    if (main !== null) rootRef(main);
  }, [rootRef, scroller]);

  const items = buildMemoryTimeline(memories, arriving);
  if (items.length === 0 && !isError) return null;
  return (
    <div className="relative flex flex-col">
      <div
        aria-hidden="true"
        className="to-ink-sepia-soft/60 absolute top-0 bottom-0 left-[3.25rem] w-0.5 rounded-full bg-gradient-to-b from-transparent sm:left-[5.75rem]"
      />
      <div ref={sentinelRef} aria-hidden="true" />
      {isError && (
        <p className="text-muted-foreground pb-3 text-sm">
          Could not load past sessions.
        </p>
      )}
      <ul className="flex flex-col">
        {items.map((item, idx) => {
          if (item.kind === "month")
            return (
              <MonthRow key={`month-${item.label}-${idx}`} label={item.label} />
            );
          if (item.kind === "gap")
            return <GapRow key={`gap-${idx}`} weeks={item.weeks} />;
          const { entry } = item.memory;
          return (
            <MemoryRow
              key={entryKey(entry, idx)}
              memory={item.memory}
              label={labelOf(entry)}
              href={occurrenceHref(entry, slug)}
              todayIso={todayIso}
              canManage={canManage}
              arriving={item.arriving}
            />
          );
        })}
      </ul>
    </div>
  );
}
