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
