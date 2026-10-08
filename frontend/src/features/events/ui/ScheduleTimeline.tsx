// frontend/src/features/events/ui/ScheduleTimeline.tsx
import { ChevronDown } from "lucide-react";
import { type MouseEvent as ReactMouseEvent, useState } from "react";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import {
  presenterIdsOf,
  resolveLabel,
} from "@/features/events/domain/schedule";
import { pageItems } from "@/features/events/ui/page-items";
import { shortDayLabel } from "@/features/events/ui/schedule-dates";
import {
  type MenuState,
  ScheduleContextMenu,
} from "@/features/events/ui/ScheduleContextMenu";
import type { TimelineItem } from "@/features/events/ui/timeline/timeline";
import {
  buildTimeline,
  entryKey,
  occurrenceHref,
} from "@/features/events/ui/timeline/timeline";
import {
  rowGridClass,
  TimelineRow,
} from "@/features/events/ui/timeline/TimelineRow";
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

function TodayOrigin({ todayIso }: Readonly<{ todayIso: string }>) {
  const label = shortDayLabel(todayIso);
  return (
    <div className={cn(rowGridClass, "items-center pb-3")}>
      <span className="bg-primary text-primary-foreground justify-self-end rounded-full px-2 py-0.5 text-xs font-semibold">
        Today
      </span>
      <span
        aria-hidden="true"
        className="timeline-beacon bg-primary relative left-[3px] size-3 rounded-full"
      />
      <span className="text-muted-foreground text-sm">{label}</span>
    </div>
  );
}

function MonthRow({ label }: Readonly<{ label: string }>) {
  return (
    <li
      role="presentation"
      aria-hidden="true"
      className={cn("timeline-row pt-1 pb-2", rowGridClass)}
    >
      <span className="font-display text-muted-foreground justify-self-end text-[11px] font-bold tracking-widest uppercase">
        {label}
      </span>
    </li>
  );
}

function StateMessages({
  isLoading,
  isError,
  count,
}: Readonly<{ isLoading: boolean; isError: boolean; count: number }>) {
  if (isLoading)
    return <p className="text-muted-foreground pb-3 text-sm">Loading…</p>;
  if (isError) {
    return (
      <p role="alert" className="text-danger pb-3 text-sm">
        Could not load schedule.
      </p>
    );
  }
  if (count === 0) {
    return (
      <p className="text-muted-foreground pb-3 text-sm">
        No scheduled sessions in this window.
      </p>
    );
  }
  return null;
}

function GapRow({ weeks }: Readonly<{ weeks: number }>) {
  return (
    <li
      role="presentation"
      aria-hidden="true"
      className={cn("timeline-row pb-3", rowGridClass)}
    >
      <span className="text-muted-foreground col-start-3 flex items-center gap-2 text-[11px]">
        {/* A gap needs 14+ days, so weeks is always >= 2 and plural. */}
        <span>{weeks} quiet weeks</span>
        <span className="border-border-strong flex-1 border-t border-dotted" />
      </span>
    </li>
  );
}

export function ScheduleTimeline({
  slug,
  fromIso,
  toIso,
  onShowMore,
  canManage = false,
}: Readonly<Props>) {
  const [menu, setMenu] = useState<MenuState | null>(null);
  const scheduleQuery = useSchedule(slug, fromIso, toIso);
  const seriesQuery = useSeriesList(slug);
  const seriesList = pageItems(seriesQuery);

  // fromIso is start-of-today UTC (SchedulePage computes it), so it doubles as the timeline origin.
  const items = buildTimeline(scheduleQuery.data ?? [], fromIso);
  const presenterIds = [
    ...new Set(
      items.flatMap((i) => (i.kind === "entry" ? presenterIdsOf(i.entry) : [])),
    ),
  ].sort((a, b) => a.localeCompare(b));
  const usersQuery = useUsers(presenterIds);
  const users = usersQuery.data ?? {};

  function renderItem(item: TimelineItem, idx: number) {
    if (item.kind === "month")
      return <MonthRow key={`month-${item.label}-${idx}`} label={item.label} />;
    if (item.kind === "gap")
      return <GapRow key={`gap-${idx}`} weeks={item.weeks} />;
    const label = resolveLabel(item.entry, seriesList);
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
    <div className="relative flex flex-col">
      <div
        aria-hidden="true"
        className="from-primary via-border-strong absolute top-1 bottom-12 left-[3.25rem] w-0.5 rounded-full bg-gradient-to-b to-transparent sm:left-[5.75rem]"
      />
      <TodayOrigin todayIso={fromIso} />
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
      {onShowMore && (
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
      )}
    </div>
  );
}
