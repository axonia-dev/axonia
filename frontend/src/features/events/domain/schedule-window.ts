/**
 * Pure helpers for computing a rolling schedule window.
 * Accept a `now` parameter so callers can control time in tests.
 */

/** ISO instant for the start of today in UTC (midnight). */
export function startOfTodayUtc(now: Date): string {
  return new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()),
  ).toISOString();
}

/** Shift an ISO instant forward by `days` calendar days (UTC). */
export function addDaysIso(isoBase: string, days: number): string {
  const d = new Date(isoBase);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString();
}

const DAY_MS = 86_400_000;

/** How far ahead the timeline ever reaches: three years of planned and projected sessions. */
export const HORIZON_DAYS = 1095;
/** The timeline loads ahead in steps of this many days. */
export const HORIZON_STEP_DAYS = 90;

/** The days ahead the timeline must load for `atMs` to be in it: whole steps, never past the horizon. */
export function horizonDaysFor(fromIso: string, atMs: number): number {
  const days = Math.ceil((atMs - new Date(fromIso).getTime()) / DAY_MS) + 1;
  const steps = Math.max(1, Math.ceil(days / HORIZON_STEP_DAYS));
  return Math.min(steps * HORIZON_STEP_DAYS, HORIZON_DAYS);
}
