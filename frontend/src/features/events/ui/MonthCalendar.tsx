import { type ReactNode, useState } from "react";
import { useNavigate } from "react-router-dom";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useScheduleActivity } from "@/features/events/api/use-schedule-activity";
import { useSeriesList } from "@/features/events/api/use-series";
import {
  type GridCell,
  groupByDate,
  monthGrid,
  monthWindow,
} from "@/features/events/domain/month-grid";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { nextUpEntry } from "@/features/events/domain/schedule";
import type { MonthPosition } from "@/features/events/domain/schedule-position";
import {
  formatMonthParam,
  monthPositionOf,
} from "@/features/events/domain/schedule-position";
import {
  CALENDAR_BLOCK,
  PANEL_LEFT,
  weekRowsVar,
} from "@/features/events/ui/calendar-layout";
import { CalendarDndProvider } from "@/features/events/ui/CalendarDnd";
import { DayCell } from "@/features/events/ui/DayCell";
import {
  DayDetail,
  SelectedSessionPanel,
} from "@/features/events/ui/DayDetail";
import { MonthNav } from "@/features/events/ui/MonthNav";
import { pageItems, queryItems } from "@/features/events/ui/page-items";
import { longDayLabel } from "@/features/events/ui/schedule-dates";
import { chipHref } from "@/features/events/ui/ScheduleChip";
import {
  ContextMenuAdapter,
  type MenuState,
} from "@/features/events/ui/ScheduleContextMenu";
import {
  presenterIdsFor,
  selectionProps,
  useSessionSelection,
} from "@/features/events/ui/session-selection";
import { useUsers } from "@/features/identity/api/use-users";
import { cn } from "@/lib/cn";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] as const;

/** Enables organizer drag-to-reschedule; renders the grid untouched otherwise. */
function CalendarGrid({
  slug,
  canManage,
  children,
}: Readonly<{ slug: string; canManage: boolean; children: ReactNode }>) {
  if (!canManage) {
    return <>{children}</>;
  }
  return <CalendarDndProvider slug={slug}>{children}</CalendarDndProvider>;
}

function todayIso(): string {
  const d = new Date();
  const utc = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
  return new Date(utc).toISOString().slice(0, 10);
}

/** Falls back to the first of the shown month when the picked day is outside it. */
function selectedDateFor(picked: string, month: MonthPosition): string {
  const shownMonth = formatMonthParam(month);
  return picked.startsWith(shownMonth) ? picked : `${shownMonth}-01`;
}

function ScheduleStatus({
  isLoading,
  isError,
}: Readonly<{ isLoading: boolean; isError: boolean }>) {
  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load schedule.
      </p>
    );
  }
  return null;
}

export function MonthCalendar({
  slug,
  month,
  onMonthChange,
  canManage = false,
  canStartSeries = false,
  highlightedDate,
  onCreateSeries,
  onCreateOneOff,
}: Readonly<{
  slug: string;
  /** The month shown. Owned by SchedulePage, which holds the route's parameters. */
  month: MonthPosition;
  onMonthChange: (month: MonthPosition) => void;
  canManage?: boolean;
  canStartSeries?: boolean;
  /** ISO date the parent currently has a creation form open for, visually marked with a ring. */
  highlightedDate?: string;
  /** Called when the user picks a day to start a new series from the context menu. */
  onCreateSeries?: (iso: string) => void;
  /** Called when the user picks a day to start a new one-off session from the context menu. */
  onCreateOneOff?: (iso: string) => void;
}>) {
  const [picked, setPicked] = useState<string>(() => todayIso());
  const [menu, setMenu] = useState<MenuState | null>(null);
  const contextEnabled = canManage || canStartSeries;
  const navigate = useNavigate();

  const { fromIso, toIso } = monthWindow(month.year, month.monthIndex0);
  const scheduleQuery = useSchedule(slug, fromIso, toIso);
  const seriesQuery = useSeriesList(slug);
  const activityQuery = useScheduleActivity(slug);

  const selectedDate = selectedDateFor(picked, month);

  const entries = scheduleQuery.data ?? [];
  const seriesList = pageItems(seriesQuery);
  const cells = monthGrid(month.year, month.monthIndex0);
  const today = todayIso();

  const byDate = groupByDate(entries);
  const selection = useSessionSelection(
    byDate,
    `${month.year}-${month.monthIndex0}`,
  );
  const usersQuery = useUsers(presenterIdsFor(selection.entry));
  const users = usersQuery.data ?? {};

  /** Selecting a day outside the shown month follows it there, rather than being discarded. */
  function selectDay(cell: GridCell) {
    setPicked(cell.isoDate);
    if (!cell.inMonth) {
      onMonthChange(monthPositionOf(cell.isoDate));
    }
  }

  function openEntry(entry: ScheduleEntry) {
    const href = chipHref(entry, slug);
    if (href !== null) void navigate(href);
  }

  function nextUpFor(): ScheduleEntry | null {
    const showsToday = cells.some((c) => c.isoDate === today && c.inMonth);
    return showsToday ? nextUpEntry(entries) : null;
  }
  const nextUp = nextUpFor();

  return (
    <div data-testid="month-calendar" className="flex flex-col gap-3">
      <div className={CALENDAR_BLOCK}>
        <MonthNav
          month={month}
          activeMonths={queryItems(activityQuery)}
          onMonthChange={onMonthChange}
        />
      </div>

      <ScheduleStatus
        isLoading={scheduleQuery.isLoading}
        isError={scheduleQuery.isError}
      />

      <div className={cn("grid grid-cols-7", CALENDAR_BLOCK)}>
        {WEEKDAYS.map((day) => (
          <div
            key={day}
            className="text-muted-foreground py-1 text-center text-xs font-medium"
          >
            {day}
          </div>
        ))}
      </div>

      <div className="relative">
        <CalendarGrid slug={slug} canManage={canManage}>
          <div
            style={weekRowsVar(cells.length)}
            className={cn(
              "bg-border-strong border-border-strong grid grid-cols-7 gap-px overflow-hidden rounded-lg border",
              CALENDAR_BLOCK,
            )}
          >
            {cells.map((cell) => (
              <DayCell
                key={cell.isoDate}
                cell={cell}
                entries={byDate.get(cell.isoDate) ?? []}
                isToday={cell.isoDate === today}
                selected={selectedDate === cell.isoDate}
                highlighted={highlightedDate === cell.isoDate}
                dayLabel={longDayLabel(cell.isoDate, true)}
                onSelectDay={() => selectDay(cell)}
                seriesList={seriesList}
                canManage={canManage}
                nextUpEntry={nextUp}
                panelId={selection.desktopPanelId}
                onOpen={openEntry}
                onSelect={(entry) => selection.select(entry, "desktop")}
                {...selectionProps(selection.selectedKey)}
                {...(contextEnabled
                  ? {
                      onContextMenu: (target, x, y) =>
                        setMenu({ target, x, y }),
                    }
                  : {})}
              />
            ))}
          </div>
        </CalendarGrid>

        {selection.entry !== null && (
          <SelectedSessionPanel
            selected={selection.entry}
            seriesList={seriesList}
            slug={slug}
            users={users}
            panelId={selection.desktopPanelId}
            focusOnOpen={selection.focusOn("desktop")}
            onClose={selection.close}
            style={{ left: PANEL_LEFT }}
            className="absolute top-0 hidden max-h-full w-[384px] md:flex"
          />
        )}
      </div>

      {menu && (
        <ContextMenuAdapter
          slug={slug}
          menu={menu}
          canManage={canManage}
          canStartSeries={canStartSeries}
          onClose={() => setMenu(null)}
          onCreateSeries={onCreateSeries}
          onCreateOneOff={onCreateOneOff}
        />
      )}

      <DayDetail
        selectedDate={selectedDate}
        dayEntries={byDate.get(selectedDate) ?? []}
        seriesList={seriesList}
        slug={slug}
        onOpen={openEntry}
        onSelect={(entry) => selection.select(entry, "phone")}
        panel={{
          selected: selection.entry,
          users,
          panelId: selection.phonePanelId,
          focusOnOpen: selection.focusOn("phone"),
          onClose: selection.close,
        }}
      />
    </div>
  );
}
