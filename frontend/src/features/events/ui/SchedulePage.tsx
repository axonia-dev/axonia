import { CalendarSync, ChevronDown, MoreHorizontal } from "lucide-react";
import { useEffect, useState } from "react";
import {
  type Key,
  Menu as AriaMenu,
  MenuTrigger,
  Popover,
} from "react-aria-components";
import { useSearchParams } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { IconButton } from "@/components/ui/IconButton";
import { MenuItem, menuPopoverClass } from "@/components/ui/Menu";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { useContributionPolicy } from "@/features/community/api/use-contribution-policy";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import type { MonthPosition } from "@/features/events/domain/schedule-position";
import {
  currentMonth,
  formatMonthParam,
  MAX_WINDOW_DAYS,
  MIN_WINDOW_DAYS,
  parseMonthParam,
  parseWindowDays,
} from "@/features/events/domain/schedule-position";
import {
  addDaysIso,
  startOfTodayUtc,
} from "@/features/events/domain/schedule-window";
import {
  CALENDAR_BLOCK,
  TIMELINE_BLOCK,
} from "@/features/events/ui/calendar-layout";
import { CalendarSubscribeDialog } from "@/features/events/ui/CalendarSubscribeDialog";
import { MonthCalendar } from "@/features/events/ui/MonthCalendar";
import { OneOffSessionForm } from "@/features/events/ui/OneOffSessionForm";
import type { ScheduleView } from "@/features/events/ui/schedule-preferences";
import {
  parseView,
  storedMonth,
  storedView,
  storeMonth,
  storeView,
} from "@/features/events/ui/schedule-preferences";
import { ScheduleTimeline } from "@/features/events/ui/ScheduleTimeline";
import { SeriesForm } from "@/features/events/ui/SeriesForm";
import { cn } from "@/lib/cn";

const VIEW_OPTIONS = [
  { id: "timeline", label: "Timeline" },
  { id: "calendar", label: "Calendar" },
] as const;

/** URL first, then the sticky preference, then the calendar. */
function resolveView(raw: string | null): ScheduleView {
  return parseView(raw) ?? storedView() ?? "calendar";
}

/** URL first, then the tab-scoped position, then the month happening now. */
function resolveMonth(raw: string | null, slug: string): MonthPosition {
  return parseMonthParam(raw) ?? storedMonth(slug) ?? currentMonth(new Date());
}

/** Closed state has no date to avoid passing undefined to exactOptionalPropertyTypes props. */
type ModalState = { open: false } | { open: true; date?: string };

export function SchedulePage({ slug }: Readonly<{ slug: string }>) {
  const [searchParams, setSearchParams] = useSearchParams();
  const view = resolveView(searchParams.get("view"));
  const windowDays = parseWindowDays(searchParams.get("days"));
  const month = resolveMonth(searchParams.get("month"), slug);
  const { year, monthIndex0 } = month;

  useEffect(() => {
    storeView(view);
  }, [view]);

  useEffect(() => {
    storeMonth(slug, { year, monthIndex0 });
  }, [slug, year, monthIndex0]);

  function setParam(key: string, value: string) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set(key, value);
        return next;
      },
      { replace: true },
    );
  }

  const [seriesModal, setSeriesModal] = useState<ModalState>({ open: false });
  const [oneOffModal, setOneOffModal] = useState<ModalState>({ open: false });
  const [targetDate, setTargetDate] = useState<string | null>(null);
  const [subscribeOpen, setSubscribeOpen] = useState(false);
  const [fromIso] = useState(() => startOfTodayUtc(new Date()));

  const canManage = useIsOrganizer(slug);
  const policy = useContributionPolicy(slug);
  const canStartSeries = canManage || policy.canStartSeries;

  function openSeries(date?: string) {
    setSeriesModal(date !== undefined ? { open: true, date } : { open: true });
    setTargetDate(date ?? null);
  }

  function openOneOff(date?: string) {
    setOneOffModal(date !== undefined ? { open: true, date } : { open: true });
    setTargetDate(date ?? null);
  }

  function closeAndClear() {
    setSeriesModal({ open: false });
    setOneOffModal({ open: false });
    setTargetDate(null);
  }

  function handleShowMore() {
    setParam(
      "days",
      String(Math.min(windowDays + MIN_WINDOW_DAYS, MAX_WINDOW_DAYS)),
    );
  }

  function handleNewAction(key: Key) {
    if (key === "series") openSeries();
    if (key === "oneoff") openOneOff();
  }

  const calendarProps = {
    ...(targetDate !== null && { highlightedDate: targetDate }),
  };
  const timelineProps =
    windowDays < MAX_WINDOW_DAYS ? { onShowMore: handleShowMore } : {};

  // The page is wide enough for the calendar and the panel that stands beside it, and centres its
  // content inside that. The controls sit over the view they control rather than at an edge it
  // never reaches: the calendar's own block for the calendar, a reading width for the timeline,
  // which is one column of cards and gains nothing from the room.
  const block = view === "timeline" ? TIMELINE_BLOCK : CALENDAR_BLOCK;

  return (
    <section className="flex flex-col gap-4">
      <div
        className={cn(
          "flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between",
          block,
        )}
      >
        <h1 className="text-foreground font-display text-xl font-semibold">
          Schedule
        </h1>
        <div className="flex flex-wrap items-center gap-3">
          {canStartSeries && (
            <MenuTrigger>
              <Button variant="ghost" size="sm">
                + New
                <ChevronDown size={14} aria-hidden="true" />
              </Button>
              <Popover className={menuPopoverClass}>
                <AriaMenu onAction={handleNewAction} className="outline-none">
                  <MenuItem id="series">New series</MenuItem>
                  <MenuItem id="oneoff">One-off session</MenuItem>
                </AriaMenu>
              </Popover>
            </MenuTrigger>
          )}
          <SegmentedControl
            label="Schedule view"
            hideLabel
            value={view}
            options={VIEW_OPTIONS}
            onChange={(next) => setParam("view", next)}
          />
          {/* A neutral glyph over a labelled item, not a calendar icon: a calendar with a
              plus beside a calendar view reads as "add a session", which + New already is. */}
          <MenuTrigger>
            <IconButton label="Schedule actions">
              <MoreHorizontal size={15} />
            </IconButton>
            <Popover className={menuPopoverClass}>
              <AriaMenu
                onAction={() => setSubscribeOpen(true)}
                className="outline-none"
              >
                <MenuItem id="subscribe">
                  <CalendarSync size={14} />
                  Add to calendar
                </MenuItem>
              </AriaMenu>
            </Popover>
          </MenuTrigger>
        </div>
      </div>

      {view === "timeline" ? (
        <div className={block}>
          <ScheduleTimeline
            slug={slug}
            fromIso={fromIso}
            toIso={addDaysIso(fromIso, windowDays)}
            canManage={canManage}
            {...timelineProps}
          />
        </div>
      ) : (
        <MonthCalendar
          slug={slug}
          month={month}
          onMonthChange={(next) => setParam("month", formatMonthParam(next))}
          canManage={canManage}
          canStartSeries={canStartSeries}
          onCreateSeries={openSeries}
          onCreateOneOff={openOneOff}
          {...calendarProps}
        />
      )}

      <Dialog
        isOpen={seriesModal.open}
        onOpenChange={(o) => {
          if (!o) closeAndClear();
        }}
        title="New series"
        size="2xl"
        pinnedFooter
      >
        {() => (
          <SeriesForm
            slug={slug}
            {...(seriesModal.open && seriesModal.date !== undefined
              ? { defaultStartsOn: seriesModal.date }
              : {})}
            onSaved={closeAndClear}
          />
        )}
      </Dialog>

      <CalendarSubscribeDialog
        slug={slug}
        isOpen={subscribeOpen}
        onOpenChange={setSubscribeOpen}
      />

      <Dialog
        isOpen={oneOffModal.open}
        onOpenChange={(o) => {
          if (!o) closeAndClear();
        }}
        title="New one-off session"
        size="2xl"
        pinnedFooter
      >
        {() => (
          <OneOffSessionForm
            slug={slug}
            {...(oneOffModal.open && oneOffModal.date !== undefined
              ? { defaultDate: oneOffModal.date }
              : {})}
            onSaved={closeAndClear}
          />
        )}
      </Dialog>
    </section>
  );
}
