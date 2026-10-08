/**
 * Pure helpers for building a Monday-first calendar month grid.
 * All date arithmetic uses Date.UTC to avoid local-timezone drift.
 */

import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";

export interface GridCell {
  isoDate: string;
  inMonth: boolean;
}

/**
 * Returns a Monday-first grid for the given year/month covering the full
 * month plus leading/trailing days to fill complete 7-day weeks (35 or 42
 * cells depending on the month layout).
 */
export function monthGrid(year: number, monthIndex0: number): GridCell[] {
  const firstOfMonth = new Date(Date.UTC(year, monthIndex0, 1));
  const daysInMonth = new Date(Date.UTC(year, monthIndex0 + 1, 0)).getUTCDate();

  // Monday = 0 offset; Sunday = 6 offset
  const offset = (firstOfMonth.getUTCDay() + 6) % 7;
  const totalCells = Math.ceil((offset + daysInMonth) / 7) * 7;

  const cells: GridCell[] = [];
  for (let i = 0; i < totalCells; i++) {
    const d = new Date(Date.UTC(year, monthIndex0, 1 - offset + i));
    cells.push({
      isoDate: d.toISOString().slice(0, 10),
      inMonth: i >= offset && i < offset + daysInMonth,
    });
  }

  return cells;
}

/**
 * Returns the UTC-midnight instants for the first and first-of-next month,
 * suitable for passing to useSchedule as fromIso / toIso.
 */
export function monthWindow(
  year: number,
  monthIndex0: number,
): { fromIso: string; toIso: string } {
  return {
    fromIso: new Date(Date.UTC(year, monthIndex0, 1)).toISOString(),
    toIso: new Date(Date.UTC(year, monthIndex0 + 1, 1)).toISOString(),
  };
}

/** Buckets entries by the date they fall on, skipping any with no resolvable date. */
export function groupByDate(
  entries: ScheduleEntry[],
): Map<string, ScheduleEntry[]> {
  const byDate = new Map<string, ScheduleEntry[]>();
  for (const entry of entries) {
    const iso = entryDayIso(entry);
    if (!iso) continue;
    byDate.set(iso, [...(byDate.get(iso) ?? []), entry]);
  }
  return byDate;
}
