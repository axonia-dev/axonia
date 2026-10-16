import type { ScheduleEntry } from "@/features/events/domain/schedule";

export type Timed = Pick<
  ScheduleEntry,
  "scheduledAt" | "hasTime" | "durationMinutes"
>;

export type CountdownPhase =
  | { kind: "counting"; msLeft: number }
  | { kind: "last-hour"; msLeft: number }
  | { kind: "running" }
  | { kind: "ended" };

const SECOND = 1000;
const MINUTE = 60_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

export function endOfEntry(entry: Timed): number | null {
  if (entry.scheduledAt === null || !entry.hasTime) return null;
  return (
    new Date(entry.scheduledAt).getTime() +
    (entry.durationMinutes ?? 0) * MINUTE
  );
}

export function countdownPhase(entry: Timed, now: Date): CountdownPhase | null {
  const end = endOfEntry(entry);
  if (end === null || entry.scheduledAt === null) return null;
  const msLeft = new Date(entry.scheduledAt).getTime() - now.getTime();
  if (now.getTime() >= end) return { kind: "ended" };
  if (msLeft <= 0) return { kind: "running" };
  return msLeft <= HOUR
    ? { kind: "last-hour", msLeft }
    : { kind: "counting", msLeft };
}

/** The next phase change: the last hour opening, the start, then the end. Ticks within a phase are
 *  the countdown's own business, so the page is not woken every second. */
export function countdownWakeAt(entry: Timed, now: Date): number | null {
  const phase = countdownPhase(entry, now);
  if (phase === null || phase.kind === "ended") return null;
  if (phase.kind === "running") return endOfEntry(entry);
  const startMs = now.getTime() + phase.msLeft;
  return phase.kind === "counting" ? startMs - HOUR : startMs;
}

export function hasEnded(entry: Timed, now: Date): boolean {
  return countdownPhase(entry, now)?.kind === "ended";
}

export function countdownParts(msLeft: number) {
  return {
    days: Math.floor(msLeft / DAY),
    hours: Math.floor((msLeft % DAY) / HOUR),
    minutes: Math.floor((msLeft % HOUR) / MINUTE),
    seconds: Math.floor((msLeft % MINUTE) / SECOND),
  };
}

const two = (n: number) => String(n).padStart(2, "0");

export function lastHourLabel(msLeft: number): string {
  const { minutes, seconds } = countdownParts(msLeft);
  return `Starts in ${two(minutes)}:${two(seconds)}`;
}

function unit(count: number, name: string): string {
  return `${count} ${name}${count === 1 ? "" : "s"}`;
}

export function countdownSpeech(msLeft: number): string {
  const { days, hours, minutes } = countdownParts(msLeft);
  if (days > 0) return `starts in ${unit(days, "day")} ${unit(hours, "hour")}`;
  if (hours > 0) {
    return `starts in ${unit(hours, "hour")} ${unit(minutes, "minute")}`;
  }
  if (minutes === 0) return "starts in under a minute";
  return `starts in ${unit(minutes, "minute")}`;
}
