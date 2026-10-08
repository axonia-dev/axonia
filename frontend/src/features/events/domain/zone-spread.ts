import type { MemberZone } from "@/features/events/domain/member-zone";
import { offsetMinutes } from "@/features/events/domain/zoned-instant";
import { cityOf } from "@/lib/zones";

const MINUTES_IN_DAY = 1440;

export interface ZoneRow {
  zone: string;
  city: string;
  count: number;
  /** `HH:MM` in this zone. */
  localStart: string;
  localEnd: string;
  /** The local weekday, stated only where it differs from the day the session itself falls on. */
  day: string | null;
  /** Where the session sits in this zone's own day, as a percentage of it. */
  offsetPct: number;
  widthPct: number;
}

/**
 * One candidate instant, read in each zone a community spans.
 *
 * Ordered west to east by UTC offset, which for a single instant is the order of local times, so
 * the first and last rows are the two ends of the spread. Sorting on the clock face instead would
 * file a next-day 05:00 ahead of a same-day 09:00 and invert them.
 *
 * `sessionZone` decides whose day counts as "the" day, so only a row falling on another one states
 * a weekday. It is the zone the organizer is typing in, never the first zone of the list, which is
 * sorted by name and is nobody in particular.
 */
export function projectZones(
  zones: ReadonlyArray<MemberZone>,
  startsAt: Date,
  durationMinutes: number,
  sessionZone: string,
): ZoneRow[] {
  if (Number.isNaN(startsAt.getTime())) return [];
  const endsAt = new Date(startsAt.getTime() + durationMinutes * 60_000);
  const sessionDay = dayIn(startsAt, sessionZone);
  return zones
    .map((entry) => {
      const localStart = hourIn(startsAt, entry.zone);
      const localDay = dayIn(startsAt, entry.zone);
      return {
        utcOffset: offsetMinutes(startsAt, entry.zone),
        row: {
          zone: entry.zone,
          city: cityOf(entry.zone),
          count: entry.count,
          localStart,
          localEnd: hourIn(endsAt, entry.zone),
          day: localDay === sessionDay ? null : localDay,
          offsetPct: (minutesOf(localStart) / MINUTES_IN_DAY) * 100,
          widthPct: (durationMinutes / MINUTES_IN_DAY) * 100,
        },
      };
    })
    .sort((a, b) => a.utcOffset - b.utcOffset)
    .map((placed) => placed.row);
}

/**
 * Both ends of the spread, named. Moving an hour means knowing who is earliest and who is latest,
 * so those two are what the collapsed line states, and nothing here grades either of them: an
 * evening slot is what a good many communities meet in on purpose.
 *
 * Null below two zones. A community that reads one clock has no spread to state, and a line saying
 * so would be a line about nothing.
 *
 * The count is dropped at exactly two, where naming both ends has already said how many there are.
 */
export function spreadSummary(rows: ReadonlyArray<ZoneRow>): string | null {
  const first = rows[0];
  const last = rows.at(-1);
  if (first === undefined || last === undefined || rows.length < 2) return null;
  const head = `${end(first, first.localStart)} to ${end(last, last.localEnd)}`;
  return rows.length === 2 ? head : `${head} across ${rows.length} zones`;
}

function end(row: ZoneRow, time: string): string {
  const day = row.day === null ? "" : ` ${row.day}`;
  return `${time}${day} in ${row.city}`;
}

function partsIn(at: Date, timeZone: string) {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone,
    weekday: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(at);
}

function hourIn(at: Date, timeZone: string): string {
  const parts = partsIn(at, timeZone);
  const value = (type: string) =>
    parts.find((part) => part.type === type)?.value ?? "00";
  // `hour12: false` reports midnight as 24 in several engines, which would read as 24:00.
  const hour = value("hour") === "24" ? "00" : value("hour");
  return `${hour}:${value("minute")}`;
}

function dayIn(at: Date, timeZone: string): string {
  return (
    partsIn(at, timeZone).find((part) => part.type === "weekday")?.value ?? ""
  );
}

function minutesOf(time: string): number {
  const [hours, minutes] = time.split(":").map(Number);
  return (hours ?? 0) * 60 + (minutes ?? 0);
}
