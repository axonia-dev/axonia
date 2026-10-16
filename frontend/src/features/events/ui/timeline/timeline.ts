import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  openSpotLabel,
  slotsOf,
  timeLabel,
} from "@/features/events/domain/schedule";
import { isOpen } from "@/features/events/domain/slot";

const DAY_MS = 86_400_000;
const WEEK_MS = 7 * DAY_MS;
const QUIET_GAP_DAYS = 14;
const VISIBLE_STATUSES = new Set(["SCHEDULED", "HELD"]);

export type TimelineItem =
  | { kind: "entry"; entry: ScheduleEntry; nextUp: boolean }
  | { kind: "month"; label: string }
  | { kind: "gap"; weeks: number };

export interface EntryDateParts {
  weekdayShort: string;
  day: number;
  relative: string | null;
  relativeShort: string | null;
}

export interface SneakPeek {
  title: string;
  presenterId: string | null;
  more: number;
}

/**
 * What an entry is ordered by: its instant, or its bare anchor for one carrying none, which then
 * sorts to the head of its own day. Ordering only - the day an entry is shown under is
 * {@link entryDayIso}'s answer, read on the clock that governs the occurrence rather than on UTC.
 */
export function entrySortKey(entry: ScheduleEntry): string {
  return entry.scheduledAt ?? entry.occurrenceDate ?? "";
}

/** The instant a timeline row stands for, as its `data-at` carries it. */
export function entryAtMs(entry: ScheduleEntry): number {
  return new Date(entrySortKey(entry)).getTime();
}

function utcMidnight(iso: string): number {
  const d = new Date(iso);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
}

function startOfIsoWeekUtc(iso: string): number {
  const mondayOffset = (new Date(iso).getUTCDay() + 6) % 7;
  return utcMidnight(iso) - mondayOffset * DAY_MS;
}

export function monthKey(iso: string): string {
  const d = new Date(iso);
  return `${d.getUTCFullYear()}-${d.getUTCMonth()}`;
}

function relativeLabel(dateIso: string, todayIso: string): string | null {
  const days = Math.round(
    (utcMidnight(dateIso) - utcMidnight(todayIso)) / DAY_MS,
  );
  if (days < 0) return null;
  if (days === 0) return "today";
  if (days === 1) return "tomorrow";
  if (days <= 6) return `in ${days} days`;
  const weeks = Math.round(
    (startOfIsoWeekUtc(dateIso) - startOfIsoWeekUtc(todayIso)) / WEEK_MS,
  );
  return weeks === 1 ? "next week" : null;
}

const SHORT_LABELS = new Map<string, string>([
  ["today", "today"],
  ["tomorrow", "tmrw"],
  ["next week", "next wk"],
]);

function shortRelativeLabel(relative: string | null): string | null {
  if (relative === null) return null;
  return (
    SHORT_LABELS.get(relative) ?? relative.replace(/^in (\d+) days$/, "in $1d")
  );
}

export function dateParts(dateIso: string, todayIso: string): EntryDateParts {
  const d = new Date(dateIso);
  const relative = relativeLabel(dateIso, todayIso);
  return {
    weekdayShort: d.toLocaleDateString("en-US", {
      weekday: "short",
      timeZone: "UTC",
    }),
    day: d.getUTCDate(),
    relative,
    relativeShort: shortRelativeLabel(relative),
  };
}

export function isShownOnTimeline(entry: ScheduleEntry): boolean {
  return VISIBLE_STATUSES.has(entry.status);
}

/** The quiet weeks between two days, or null when they are under two weeks apart. */
export function quietWeeks(
  earlierIso: string,
  laterIso: string,
): number | null {
  const gapDays = Math.round(
    (utcMidnight(laterIso) - utcMidnight(earlierIso)) / DAY_MS,
  );
  return gapDays >= QUIET_GAP_DAYS ? Math.round(gapDays / 7) : null;
}

export function monthLabel(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "long",
    timeZone: "UTC",
  });
}

/** "Sun 18 October": the day a ribbon's value text names. */
export function ribbonDayLabel(iso: string): string {
  const { weekdayShort, day } = dateParts(iso, iso);
  return `${weekdayShort} ${day} ${monthLabel(iso)}`;
}

/** Orders visible entries and interleaves month markers and quiet-stretch gaps. */
export function buildTimeline(
  entries: ScheduleEntry[],
  todayIso: string,
): TimelineItem[] {
  const dated = entries
    .filter(isShownOnTimeline)
    .flatMap((e) => {
      const iso = entryDayIso(e);
      return iso === "" ? [] : [{ entry: e, iso, sortKey: entrySortKey(e) }];
    })
    .sort((a, b) => a.sortKey.localeCompare(b.sortKey));

  const items: TimelineItem[] = [];
  const todayMonth = monthKey(todayIso);
  let monthRef = todayMonth;
  let prevIso: string | null = null;
  let nextUpAssigned = false;

  for (const { entry, iso } of dated) {
    const weeks = prevIso === null ? null : quietWeeks(prevIso, iso);
    if (weeks !== null) items.push({ kind: "gap", weeks });
    const month = monthKey(iso);
    if (month !== monthRef && month !== todayMonth) {
      items.push({ kind: "month", label: monthLabel(iso) });
    }
    monthRef = month;
    const nextUp: boolean =
      !nextUpAssigned && !entry.virtual && entry.status !== "HELD";
    nextUpAssigned = nextUpAssigned || nextUp;
    items.push({ kind: "entry", entry, nextUp });
    prevIso = iso;
  }
  return items;
}

export function sneakPeek(entry: ScheduleEntry): SneakPeek | null {
  const filled = slotsOf(entry).filter((s) => !isOpen(s) && s.title !== null);

  const first = filled[0];
  if (first === undefined) return null;
  return {
    title: first.title ?? "",
    presenterId: first.presenterUserIds[0] ?? null,
    more: filled.length - 1,
  };
}

/** Where a timeline entry navigates: the real session, or a ghost's preview. */
export function occurrenceHref(entry: ScheduleEntry, slug: string): string {
  if (entry.sessionId !== null) {
    return `/communities/${slug}/sessions/${entry.sessionId}`;
  }
  return `/communities/${slug}/series/${entry.seriesId}/occurrences/${entry.occurrenceDate}`;
}

export function entryKey(entry: ScheduleEntry, idx: number): string {
  return entry.sessionId !== null
    ? String(entry.sessionId)
    : `${entry.seriesId ?? "x"}-${entry.occurrenceDate ?? idx}`;
}

export function accessibleName(
  entry: ScheduleEntry,
  label: string,
  countdown?: string,
): string {
  const parts = [label];
  const iso = entryDayIso(entry);
  if (iso !== "") {
    parts.push(
      new Date(iso).toLocaleDateString("en-US", {
        weekday: "long",
        month: "long",
        day: "numeric",
        timeZone: "UTC",
      }),
    );
  }
  const time = timeLabel(entry);
  if (time !== null) parts.push(time);
  const spots = openSpotLabel(entry);
  if (spots !== null) parts.push(spots);
  if (entry.virtual) parts.push("projected");
  if (entry.status === "HELD") parts.push("held");
  if (countdown !== undefined) parts.push(countdown);
  return parts.join(", ");
}
