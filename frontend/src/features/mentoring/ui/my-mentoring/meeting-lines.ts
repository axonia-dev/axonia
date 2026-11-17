import type { PairingSummary } from "@/features/mentoring/domain/mentoring";
import { dayAndMonth } from "@/lib/declared-on";

export interface MeetingLine {
  big: string;
  small: string;
  tone: "plain" | "warning";
}

const DAY_MS = 86_400_000;

const WEEKDAY_AND_DAY = new Intl.DateTimeFormat("en-GB", {
  weekday: "short",
  day: "numeric",
  timeZone: "UTC",
});

/** Calendar days from `today` to `day`, both ISO days such as "2026-11-17". */
export function daysBetween(today: string, day: string): number {
  return Math.round((Date.parse(day) - Date.parse(today)) / DAY_MS);
}

function inDays(days: number): string {
  return days === 1 ? "tomorrow" : `in ${days} days`;
}

function noDateLine(lastMetOn: string | null): MeetingLine {
  const small =
    lastMetOn === null
      ? "no meeting yet"
      : `last met ${dayAndMonth(lastMetOn)}`;
  return { big: "No date set", small, tone: "warning" };
}

/** The headline of an ongoing pairing: when the two meet next, or what is owed. */
export function nextMeetingLine(
  pairing: PairingSummary,
  today: string,
): MeetingLine {
  const { nextMeeting } = pairing;
  if (nextMeeting === null) return noDateLine(pairing.lastMetOn);
  const day = WEEKDAY_AND_DAY.format(new Date(nextMeeting.on));
  if (nextMeeting.passed) {
    return { big: day, small: "notes to write", tone: "warning" };
  }
  const kind = pairing.notes === 0 ? "first meeting" : "next meeting";
  const days = daysBetween(today, nextMeeting.on);
  if (days <= 0) return { big: "Today", small: kind, tone: "plain" };
  return { big: day, small: `${kind} · ${inDays(days)}`, tone: "plain" };
}

export function lapseLine(lapsesOn: string, today: string): string {
  const days = daysBetween(today, lapsesOn);
  if (days <= 0) return "lapses today";
  return days === 1 ? "1 day left" : `${days} days left`;
}
