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
import { CommunitySavedSessions } from "@/features/bookmarks/ui/CommunitySavedSessions";
import { useContributionPolicy } from "@/features/community/api/use-contribution-policy";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import type { MonthPosition } from "@/features/events/domain/schedule-position";
import {
  currentMonth,
  formatMonthParam,
  parseMonthParam,
} from "@/features/events/domain/schedule-position";
import { startOfTodayUtc } from "@/features/events/domain/schedule-window";
import {
  CALENDAR_BLOCK,
  TIMELINE_BLOCK,
} from "@/features/events/ui/calendar-layout";
import { CalendarSubscribeDialog } from "@/features/events/ui/CalendarSubscribeDialog";
import { CalendarSyncChip } from "@/features/events/ui/CalendarSyncChip";
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

const SAVED_PARAM = "saved";

const SAVED_VIEW = "saved";

const VIEW_OPTIONS = [
  { id: "timeline", label: "Timeline" },
  { id: "calendar", label: "Calendar" },
  { id: SAVED_VIEW, label: "Saved" },
] as const;

/** Saved rides its own URL flag so the remembered calendar or timeline survives a visit to it. */
function viewChange(next: string): Record<string, string | null> {
  return next === SAVED_VIEW
    ? { [SAVED_PARAM]: "1" }
    : { view: next, [SAVED_PARAM]: null };
}

/** URL first, then the sticky preference, then the calendar. */
function resolveView(raw: string | null): ScheduleView {
  return parseView(raw) ?? storedView() ?? "calendar";
}

/** URL first, then the tab-scoped position, then the month happening now. */
function resolveMonth(raw: string | null, slug: string): MonthPosition {
  return parseMonthParam(raw) ?? storedMonth(slug) ?? currentMonth(new Date());
}

/** Saved sessions are one column of cards, read at the timeline's width. */
function blockFor(saved: boolean, view: ScheduleView) {
  return saved || view === "timeline" ? TIMELINE_BLOCK : CALENDAR_BLOCK;
}

/** Closed state has no date to avoid passing undefined to exactOptionalPropertyTypes props. */
type ModalState = { open: false } | { open: true; date?: string };

export function SchedulePage({ slug }: Readonly<{ slug: string }>) {
  const [searchParams, setSearchParams] = useSearchParams();
  const view = resolveView(searchParams.get("view"));
  const month = resolveMonth(searchParams.get("month"), slug);
  const saved = searchParams.get(SAVED_PARAM) === "1";
  const { year, monthIndex0 } = month;

  useEffect(() => {
    storeView(view);
  }, [view]);

  useEffect(() => {
    storeMonth(slug, { year, monthIndex0 });
  }, [slug, year, monthIndex0]);

  function setParams(changes: Record<string, string | null>) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        for (const [key, value] of Object.entries(changes)) {
          if (value === null) {
            next.delete(key);
          } else {
            next.set(key, value);
          }
        }
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

  function handleNewAction(key: Key) {
    if (key === "series") openSeries();
    if (key === "oneoff") openOneOff();
  }

  const calendarProps = {
    ...(targetDate !== null && { highlightedDate: targetDate }),
  };

  // The page is wide enough for the calendar and the panel that stands beside it, and centres its
  // content inside that. The controls sit over the view they control rather than at an edge it
  // never reaches: the calendar's own block for the calendar, a reading width for the timeline,
  // which is one column of cards and gains nothing from the room.
  const block = blockFor(saved, view);

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
          <CalendarSyncChip
            slug={slug}
            onManage={() => setSubscribeOpen(true)}
          />
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
            value={saved ? SAVED_VIEW : view}
            options={VIEW_OPTIONS}
            onChange={(next) => setParams(viewChange(next))}
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

      {saved && (
        <div className={block}>
          <CommunitySavedSessions slug={slug} />
        </div>
      )}
      {!saved && view === "timeline" && (
        <div className={block}>
          <ScheduleTimeline
            slug={slug}
            fromIso={fromIso}
            canManage={canManage}
          />
        </div>
      )}
      {!saved && view === "calendar" && (
        <MonthCalendar
          slug={slug}
          month={month}
          onMonthChange={(next) => setParams({ month: formatMonthParam(next) })}
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
