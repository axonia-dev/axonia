import type { StatusTone } from "@/components/ui/StatusChip";
import { governingZone } from "@/features/events/domain/reschedule-target";
import type { Session } from "@/features/events/domain/session";
import {
  isCancelled,
  isHeld,
  isRunning,
  isSkipped,
  needsCloseAnswer,
} from "@/features/events/domain/session";
import { instantToZoned } from "@/features/events/domain/zoned-instant";

/**
 * What the session page is made of, which is a different question from what a slot lets a reader
 * do. `SessionMode` answers the second and is unchanged; conflating the two is what made a held
 * session render three budget guardrails against a plan nobody can change any more.
 */
export type SessionPhase = "upcoming" | "awaiting" | "held" | "closed";

export function sessionPhase(
  session: Session,
  now: Date = new Date(),
): SessionPhase {
  if (isCancelled(session) || isSkipped(session)) return "closed";
  if (isHeld(session)) return "held";
  if (needsCloseAnswer(session, now)) return "awaiting";
  return "upcoming";
}

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;

interface ChipFacts {
  tone: StatusTone;
  label: string;
}

const SETTLED: Partial<Record<Session["status"], ChipFacts>> = {
  HELD: { tone: "success", label: "Held" },
  CANCELLED: { tone: "danger", label: "Cancelled" },
  SKIPPED: { tone: "neutral", label: "Skipped" },
};

/** The whole-day gap between two `YYYY-MM-DD` dates, both read as UTC midnights so the
 *  subtraction is exact and immune to either date's own zone's DST. */
function calendarDaysBetween(fromDate: string, toDate: string): number {
  const from = new Date(`${fromDate}T00:00:00Z`).getTime();
  const to = new Date(`${toDate}T00:00:00Z`).getTime();
  return Math.round((to - from) / DAY);
}

/**
 * How far off a session still is, in the words a reader would use. A calendar-day gap on the
 * zone that governs the session, not elapsed hours on the viewer's own clock: a session three
 * hours away later tonight is "Later today", not "Tomorrow" just because midnight is closer than
 * a full day away, and a session at the same wall hour is read in the session's own zone so the
 * viewer's own zone can never tip a call to the wrong calendar day.
 */
function countdown(session: Session, now: Date): ChipFacts {
  const msAway = new Date(session.scheduledAt).getTime() - now.getTime();
  if (msAway <= 60 * MINUTE) return { tone: "success", label: "Starting soon" };
  const zone = governingZone(session);
  const nowDate = instantToZoned(now.toISOString(), zone).date;
  const targetDate = instantToZoned(session.scheduledAt, zone).date;
  const days = calendarDaysBetween(nowDate, targetDate);
  if (days === 0) return { tone: "neutral", label: "Later today" };
  if (days === 1) return { tone: "neutral", label: "Tomorrow" };
  return { tone: "neutral", label: `In ${days} days` };
}

/**
 * What the header chip says. A settled status states itself; an unsettled one states what is
 * owed - unless the session is still literally running, which is worth stating in its own right
 * rather than folded into "awaiting an answer" the instant the start time passes. The phase stays
 * `awaiting` either way (`needsCloseAnswer`, and so `sessionPhase`, are unchanged): only the chip
 * distinguishes a session still inside its own window from one that has finished and is owed one.
 *
 * The chip is where a cancelled session announces itself, rather than in a muted line at
 * the foot of its own agenda where a reader reaches it only after scrolling past the whole thing.
 */
export function sessionStatusChip(
  session: Session,
  now: Date = new Date(),
): ChipFacts {
  const settled = SETTLED[session.status];
  if (settled) return settled;
  if (needsCloseAnswer(session, now)) {
    if (isRunning(session, now)) {
      return { tone: "success", label: "Running now" };
    }
    return { tone: "warning", label: "Awaiting an answer" };
  }
  return countdown(session, now);
}
