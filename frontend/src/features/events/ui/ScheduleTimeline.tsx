// frontend/src/features/events/ui/ScheduleTimeline.tsx
import { ChevronDown } from "lucide-react";
import { type MouseEvent as ReactMouseEvent, useRef, useState } from "react";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import type { Memory } from "@/features/events/domain/memory";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  presenterIdsOf,
  resolveLabel,
} from "@/features/events/domain/schedule";
import { pageItems } from "@/features/events/ui/page-items";
import {
  type MenuState,
  ScheduleContextMenu,
} from "@/features/events/ui/ScheduleContextMenu";
import { MemoriesSection } from "@/features/events/ui/timeline/MemoriesSection";
import { ribbonValueText } from "@/features/events/ui/timeline/ribbon-value-text";
import { RibbonBar } from "@/features/events/ui/timeline/RibbonBar";
import type { TimelineItem } from "@/features/events/ui/timeline/timeline";
import {
  buildTimeline,
  entryAtMs,
  entryKey,
  occurrenceHref,
} from "@/features/events/ui/timeline/timeline";
import {
  GapRow,
  MonthRow,
  StateMessages,
  TodayOrigin,
} from "@/features/events/ui/timeline/timeline-rows";
import {
  rowGridClass,
  TimelineRow,
} from "@/features/events/ui/timeline/TimelineRow";
import { useMemoryStream } from "@/features/events/ui/timeline/use-memory-stream";
import { useTimelineScroll } from "@/features/events/ui/timeline/use-timeline-scroll";
import { useUsers } from "@/features/identity/api/use-users";
import { cn } from "@/lib/cn";

interface Props {
  slug: string;
  fromIso: string;
  toIso: string;
  /** Omitted once the window is already at its maximum: there is nothing left to show. */
  onShowMore?: () => void;
  canManage?: boolean;
}

function ShowMore({ onShowMore }: Readonly<{ onShowMore: () => void }>) {
  return (
    <div className={cn(rowGridClass, "items-center")}>
      <span
        aria-hidden="true"
        className="border-border-strong col-start-2 ml-2 h-6 border-l-2 border-dotted"
      />
      <button
        type="button"
        onClick={onShowMore}
        className="border-border-strong text-primary-strong hover:border-primary hover:bg-primary/5 col-start-3 inline-flex cursor-pointer items-center gap-1.5 justify-self-start rounded-full border px-3.5 py-1.5 text-xs font-semibold transition-colors"
      >
        <ChevronDown size={14} aria-hidden="true" />
        Show more
      </button>
    </div>
  );
}

function presenterIdsIn(items: TimelineItem[]): string[] {
  const ids = new Set(
    items.flatMap((i) => (i.kind === "entry" ? presenterIdsOf(i.entry) : [])),
  );
  return [...ids].sort((a, b) => a.localeCompare(b));
}

function loadOlderOf(
  query: ReturnType<typeof useMemoryStream>["memoriesQuery"],
) {
  if (!query.hasNextPage || query.isFetchingNextPage) return null;
  return () => void query.fetchNextPage();
}

function oldestOf(memories: Memory[]): number {
  return Math.min(Infinity, ...memories.map((m) => entryAtMs(m.entry)));
}

function entriesOf(memories: Memory[], ahead: ScheduleEntry[]) {
  return [...memories.map((memory) => memory.entry), ...ahead];
}

export function ScheduleTimeline({
  slug,
  fromIso,
  toIso,
  onShowMore,
  canManage = false,
}: Readonly<Props>) {
  const [menu, setMenu] = useState<MenuState | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const todayRef = useRef<HTMLDivElement>(null);
  const scheduleQuery = useSchedule(slug, fromIso, toIso);
  const seriesQuery = useSeriesList(slug);
  const seriesList = pageItems(seriesQuery);
  const { now, ahead, memories, arriving, memoriesQuery } = useMemoryStream(
    slug,
    scheduleQuery.data,
  );
  const nowMs = now.getTime();
  const ready = !scheduleQuery.isLoading && !memoriesQuery.isLoading;
  const { lensMs, scrollToAt, scroller } = useTimelineScroll({
    rootRef,
    todayRef,
    ready,
    memoryCount: memories.length,
  });

  // fromIso is start-of-today UTC (SchedulePage computes it), so it doubles as the timeline origin.
  const items = buildTimeline(ahead, fromIso);
  const usersQuery = useUsers(presenterIdsIn(items));
  const users = usersQuery.data ?? {};
  const labelOf = (entry: ScheduleEntry) => resolveLabel(entry, seriesList);

  function renderItem(item: TimelineItem, idx: number) {
    if (item.kind === "month")
      return <MonthRow key={`month-${item.label}-${idx}`} label={item.label} />;
    if (item.kind === "gap")
      return <GapRow key={`gap-${idx}`} weeks={item.weeks} />;
    const label = labelOf(item.entry);
    return (
      <TimelineRow
        key={entryKey(item.entry, idx)}
        entry={item.entry}
        nextUp={item.nextUp}
        label={label}
        href={occurrenceHref(item.entry, slug)}
        todayIso={fromIso}
        users={users}
        {...(canManage
          ? {
              onContextMenu: (event: ReactMouseEvent) => {
                event.preventDefault();
                setMenu({
                  target: { kind: "entry", entry: item.entry, label },
                  x: event.clientX,
                  y: event.clientY,
                });
              },
            }
          : {})}
      />
    );
  }

  return (
    <div
      ref={rootRef}
      className="relative flex flex-col [overflow-anchor:none]"
    >
      <RibbonBar
        slug={slug}
        fromIso={fromIso}
        toIso={toIso}
        nowMs={nowMs}
        lensMs={lensMs}
        hasContent={items.length > 0 || memories.length > 0}
        ready={ready}
        oldestShownMs={oldestOf(memories)}
        memoriesQuery={memoriesQuery}
        valueText={ribbonValueText(entriesOf(memories, ahead), {
          labelOf,
          nowMs,
          todayIso: fromIso,
        })}
        scrollToAt={scrollToAt}
      />
      <MemoriesSection
        memories={memories}
        arriving={arriving}
        slug={slug}
        todayIso={fromIso}
        canManage={canManage}
        labelOf={labelOf}
        loadOlder={loadOlderOf(memoriesQuery)}
        isError={memoriesQuery.isError}
        scroller={scroller}
      />
      <div className="relative flex flex-col pt-3">
        <div
          aria-hidden="true"
          className="from-primary via-border-strong absolute top-4 bottom-12 left-[3.25rem] w-0.5 rounded-full bg-gradient-to-b to-transparent sm:left-[5.75rem]"
        />
        <TodayOrigin ref={todayRef} todayIso={fromIso} atMs={nowMs} />
        <StateMessages
          isLoading={scheduleQuery.isLoading}
          isError={scheduleQuery.isError}
          count={items.length}
        />
        <ul className="flex flex-col">{items.map(renderItem)}</ul>
        {menu && (
          <ScheduleContextMenu
            slug={slug}
            target={menu.target}
            x={menu.x}
            y={menu.y}
            canManage={canManage}
            canStartSeries={false}
            onClose={() => setMenu(null)}
          />
        )}
        {onShowMore && <ShowMore onShowMore={onShowMore} />}
      </div>
    </div>
  );
}
