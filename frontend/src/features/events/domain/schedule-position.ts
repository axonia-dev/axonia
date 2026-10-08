/**
 * Pure helpers for the schedule's position: which month the calendar shows and
 * how far the timeline reaches. All month arithmetic is UTC, matching month-grid.
 */

export interface MonthPosition {
  year: number;
  monthIndex0: number;
}

export const MIN_WINDOW_DAYS = 90;
export const MAX_WINDOW_DAYS = 1095;

const MONTH_PARAM = /^(\d{4})-(\d{2})$/;

export function currentMonth(now: Date): MonthPosition {
  return { year: now.getUTCFullYear(), monthIndex0: now.getUTCMonth() };
}

export function previousMonth(m: MonthPosition): MonthPosition {
  if (m.monthIndex0 === 0) return { year: m.year - 1, monthIndex0: 11 };
  return { year: m.year, monthIndex0: m.monthIndex0 - 1 };
}

export function nextMonth(m: MonthPosition): MonthPosition {
  if (m.monthIndex0 === 11) return { year: m.year + 1, monthIndex0: 0 };
  return { year: m.year, monthIndex0: m.monthIndex0 + 1 };
}

export function sameMonth(a: MonthPosition, b: MonthPosition): boolean {
  return a.year === b.year && a.monthIndex0 === b.monthIndex0;
}

export function formatMonthParam(m: MonthPosition): string {
  return `${m.year}-${String(m.monthIndex0 + 1).padStart(2, "0")}`;
}

export function parseMonthParam(raw: string | null): MonthPosition | null {
  const match = raw === null ? null : MONTH_PARAM.exec(raw);
  if (match === null) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (month < 1 || month > 12) return null;
  return { year, monthIndex0: month - 1 };
}

/** Reads the month straight out of a `YYYY-MM-DD` date, e.g. a grid cell's
 *  `isoDate`, which is always well-formed and so never needs the nullable
 *  parsing a URL/storage value does. */
export function monthPositionOf(isoDate: string): MonthPosition {
  return {
    year: Number(isoDate.slice(0, 4)),
    monthIndex0: Number(isoDate.slice(5, 7)) - 1,
  };
}

export function parseWindowDays(raw: string | null): number {
  const days = raw === null ? Number.NaN : Number(raw);
  if (!Number.isInteger(days)) return MIN_WINDOW_DAYS;
  return Math.min(Math.max(days, MIN_WINDOW_DAYS), MAX_WINDOW_DAYS);
}
