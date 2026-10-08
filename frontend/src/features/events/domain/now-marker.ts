import { governingZone } from "@/features/events/domain/reschedule-target";
import type { RunSheetRow } from "@/features/events/domain/run-sheet";
import type { Session } from "@/features/events/domain/session";
import { instantToZoned } from "@/features/events/domain/zoned-instant";

const MINUTE = 60_000;

export interface NowMarker {
  /** The row the clock is inside. */
  rowIndex: number;
  /** How far into that row's own run the clock is: 0 as it starts, 1 as it ends. */
  fraction: number;
  /** The hour it is now, on the clock that governs the session. */
  label: string;
}

export interface NowMarkerState {
  marker: NowMarker | null;
  /** The instant the marker next has something new to say, or null once it never will again. */
  wakeAt: number | null;
}

const NOTHING: NowMarkerState = { marker: null, wakeAt: null };

/** Only a scheduled session that meets at a real hour and has an agenda can be placed on a clock. */
function followable(session: Session, rows: readonly RunSheetRow[]): boolean {
  return session.status === "SCHEDULED" && session.hasTime && rows.length > 0;
}

/**
 * The row the clock is inside, or -1 once it is past them all. A row nobody put any minutes on
 * spans no time at all and so is never the one: its start and its end are the same instant, which
 * is the next row's start.
 */
function rowAt(rows: readonly RunSheetRow[], elapsed: number): number {
  return rows.findIndex(
    (row) =>
      elapsed >= row.startMinutes &&
      elapsed < row.startMinutes + row.runMinutes,
  );
}

/**
 * Where "now" falls on a session's run sheet, and when to come back and ask again.
 *
 * The marker is placed within the row the clock is inside rather than at a share of the whole
 * sheet: a row is as tall as its minutes or as tall as its contents, whichever is more, so the
 * sheet as a whole is not a linear scale of time and a position measured against it would drift
 * further from the truth the longer the agenda got.
 *
 * It reads the hour on the session's own zone for the same reason every other hour on the page
 * does: a reader elsewhere would otherwise see a marker whose label disagrees with the block
 * start times printed right above it.
 */
export function nowMarkerState(
  session: Session,
  rows: readonly RunSheetRow[],
  now: Date,
): NowMarkerState {
  if (!followable(session, rows)) return NOTHING;

  const start = new Date(session.scheduledAt).getTime();
  const at = now.getTime();
  if (at < start) return { marker: null, wakeAt: start };

  const elapsed = (at - start) / MINUTE;
  const rowIndex = rowAt(rows, elapsed);
  const row = rows[rowIndex];
  if (row === undefined) return NOTHING;

  return {
    marker: {
      rowIndex,
      fraction: (elapsed - row.startMinutes) / row.runMinutes,
      label: instantToZoned(now.toISOString(), governingZone(session)).time,
    },
    wakeAt: (Math.floor(at / MINUTE) + 1) * MINUTE,
  };
}
