import { z } from "zod";

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

export interface RibbonWindow {
  fromIso: string;
  toIso: string;
  yearsBack: number;
}

export interface RibbonSpan {
  fromMs: number;
  toMs: number;
  hasOlder: boolean;
}

function shiftYears(iso: string, years: number): string {
  const date = new Date(iso);
  date.setUTCFullYear(date.getUTCFullYear() - years);
  return date.toISOString();
}

function shiftMonths(ms: number, months: number): number {
  const date = new Date(ms);
  date.setUTCMonth(date.getUTCMonth() + months);
  return date.getTime();
}

/** The window the ribbon asks for: a year back from today up to the future end, or one older year. */
export function ribbonWindow(
  todayIso: string,
  futureEndIso: string,
  yearsBack: number,
): RibbonWindow {
  return {
    fromIso: shiftYears(todayIso, yearsBack + 1),
    toIso: yearsBack === 0 ? futureEndIso : shiftYears(todayIso, yearsBack),
    yearsBack,
  };
}

export function ribbonSpan(
  window: RibbonWindow,
  firstSessionAt: string | null,
  todayIso: string,
): RibbonSpan {
  const windowFrom = new Date(window.fromIso).getTime();
  const today = new Date(todayIso).getTime();
  const first =
    firstSessionAt === null ? null : new Date(firstSessionAt).getTime();
  const start =
    window.yearsBack === 0
      ? Math.max(windowFrom, Math.min(first ?? today, today))
      : windowFrom;
  return {
    fromMs: start,
    toMs: new Date(window.toIso).getTime(),
    hasOlder: first !== null && first < windowFrom,
  };
}

export function fractionOf(atMs: number, span: RibbonSpan): number {
  const width = span.toMs - span.fromMs;
  if (width <= 0) return 0;
  return Math.min(1, Math.max(0, (atMs - span.fromMs) / width));
}

export function atFraction(fraction: number, span: RibbonSpan): number {
  return span.fromMs + fraction * (span.toMs - span.fromMs);
}

const msOf = (tick: RibbonTick) => new Date(tick.at).getTime();

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

export function stepMonth(
  ticks: RibbonTick[],
  currentMs: number,
  direction: 1 | -1,
): RibbonTick | null {
  const others = ticks.filter((t) => msOf(t) !== currentMs);
  return nearestTick(
    others.length > 0 ? others : ticks,
    shiftMonths(currentMs, direction),
  );
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
