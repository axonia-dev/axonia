import { z } from "zod";

import { HORIZON_DAYS } from "@/features/events/domain/schedule-window";

const ribbonTickSchema = z.object({
  at: z.string(),
  kind: z.enum(["MEMORY", "UPCOMING", "PROJECTED"]),
});
export const ribbonSchema = z.object({
  firstSessionAt: z.string().nullable(),
  ticks: z.array(ribbonTickSchema),
});

export type Ribbon = z.infer<typeof ribbonSchema>;
export type RibbonTick = z.infer<typeof ribbonTickSchema>;

const msOf = (tick: RibbonTick) => new Date(tick.at).getTime();

export interface RibbonSpan {
  fromMs: number;
  toMs: number;
}

const DAY_MS = 86_400_000;

/** The width of the rolling window the ribbon shows. */
export const RIBBON_WINDOW_MS = 365 * DAY_MS;
/** How close to an edge the lens may come before the window rolls on. */
const FOLLOW_MARGIN_MS = RIBBON_WINDOW_MS / 12;

function shiftMonths(ms: number, months: number): number {
  const date = new Date(ms);
  date.setUTCMonth(date.getUTCMonth() + months);
  return date.getTime();
}

/** Everything the ribbon can roll over: the first session, or today, up to the horizon. */
export function ribbonBounds(
  firstSessionAt: string | null,
  todayMs: number,
): RibbonSpan {
  const first =
    firstSessionAt === null ? todayMs : new Date(firstSessionAt).getTime();
  return {
    fromMs: Math.min(first, todayMs),
    toMs: todayMs + HORIZON_DAYS * DAY_MS,
  };
}

/** The window starting at `startMs`, slid back inside the bounds. */
export function windowAt(startMs: number, bounds: RibbonSpan): RibbonSpan {
  const latest = Math.max(bounds.fromMs, bounds.toMs - RIBBON_WINDOW_MS);
  const fromMs = Math.min(Math.max(startMs, bounds.fromMs), latest);
  return {
    fromMs,
    toMs: Math.min(fromMs + RIBBON_WINDOW_MS, bounds.toMs),
  };
}

/** The window with `atMs` in its middle. */
export function windowAround(atMs: number, bounds: RibbonSpan): RibbonSpan {
  return windowAt(atMs - RIBBON_WINDOW_MS / 2, bounds);
}

/**
 * The window rolled just enough to keep a lens moving `direction` a margin inside the edge it
 * heads for. A lens heading away from an edge never drags the window, so one still travelling
 * back to where a scrub sent it leaves the window where the reader put it.
 */
export function follow(
  span: RibbonSpan,
  atMs: number,
  direction: number,
  bounds: RibbonSpan,
): RibbonSpan {
  if (direction > 0 && atMs > span.toMs - FOLLOW_MARGIN_MS) {
    return windowAt(atMs + FOLLOW_MARGIN_MS - RIBBON_WINDOW_MS, bounds);
  }
  if (direction < 0 && atMs < span.fromMs + FOLLOW_MARGIN_MS) {
    return windowAt(atMs - FOLLOW_MARGIN_MS, bounds);
  }
  return span;
}

/** The UTC years the ribbon loads ticks for: those the window touches, and a quarter beyond it. */
export function ribbonYears(span: RibbonSpan): number[] {
  const first = new Date(shiftMonths(span.fromMs, -3)).getUTCFullYear();
  const last = new Date(shiftMonths(span.toMs, 3)).getUTCFullYear();
  return Array.from({ length: last - first + 1 }, (_, index) => first + index);
}

export function yearRange(year: number): { fromIso: string; toIso: string } {
  return {
    fromIso: new Date(Date.UTC(year, 0, 1)).toISOString(),
    toIso: new Date(Date.UTC(year + 1, 0, 1)).toISOString(),
  };
}

/** The ticks of every loaded year, in order, each once. */
export function mergeTicks(years: RibbonTick[][]): RibbonTick[] {
  const seen = new Set<string>();
  return years
    .flat()
    .filter((tick) => {
      const key = `${tick.kind}-${tick.at}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => msOf(a) - msOf(b));
}

export function fractionOf(atMs: number, span: RibbonSpan): number {
  const width = span.toMs - span.fromMs;
  if (width <= 0) return 0;
  return Math.min(1, Math.max(0, (atMs - span.fromMs) / width));
}

export function atFraction(fraction: number, span: RibbonSpan): number {
  return span.fromMs + fraction * (span.toMs - span.fromMs);
}

export function ticksIn(ticks: RibbonTick[], span: RibbonSpan): RibbonTick[] {
  return ticks.filter((t) => msOf(t) >= span.fromMs && msOf(t) <= span.toMs);
}

export function nearestTick(
  ticks: RibbonTick[],
  atMs: number,
): RibbonTick | null {
  let best: RibbonTick | null = null;
  for (const tick of ticks) {
    if (
      best === null ||
      Math.abs(msOf(tick) - atMs) < Math.abs(msOf(best) - atMs)
    ) {
      best = tick;
    }
  }
  return best;
}

export function stepTick(
  ticks: RibbonTick[],
  currentMs: number,
  direction: 1 | -1,
): RibbonTick | null {
  const candidates = ticks.filter((t) =>
    direction === 1 ? msOf(t) > currentMs : msOf(t) < currentMs,
  );
  return nearestTick(candidates, currentMs);
}

/** The session nearest a month on, among those that lie that way: never back against the key. */
export function stepMonth(
  ticks: RibbonTick[],
  currentMs: number,
  direction: 1 | -1,
): RibbonTick | null {
  const ahead = ticks.filter((t) =>
    direction === 1 ? msOf(t) > currentMs : msOf(t) < currentMs,
  );
  return nearestTick(ahead, shiftMonths(currentMs, direction));
}

export function monthMarks(
  span: RibbonSpan,
): { atMs: number; label: string }[] {
  const start = new Date(span.fromMs);
  let cursor = Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), 1);
  const marks: { atMs: number; label: string }[] = [];
  for (; cursor <= span.toMs; cursor = shiftMonths(cursor, 1)) {
    if (cursor >= span.fromMs) {
      marks.push({
        atMs: cursor,
        label: new Date(cursor).toLocaleDateString("en-US", {
          month: "short",
          timeZone: "UTC",
        }),
      });
    }
  }
  return marks;
}
