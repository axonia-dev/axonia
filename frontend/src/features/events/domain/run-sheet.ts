import { governingZone } from "@/features/events/domain/reschedule-target";
import type { Section } from "@/features/events/domain/section";
import {
  isRoundTable,
  sectionUsedMinutes,
} from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { instantToZoned } from "@/features/events/domain/zoned-instant";

/**
 * How long a block actually runs. A round table legitimately holds no talks and is timed by the
 * budget somebody set for it; every other section is timed by what is on it, because the run
 * sheet describes the running order as it stands rather than as it was planned.
 */
export function sectionRunMinutes(section: Section): number {
  if (isRoundTable(section)) return section.durationMinutes ?? 0;
  return sectionUsedMinutes(section);
}

/** The id a run-sheet row carries, so a map of the page can link into it. */
export function sectionAnchorId(sectionId: number): string {
  return `run-sheet-section-${sectionId}`;
}

export interface RunSheetRow {
  section: Section;
  startMinutes: number;
  runMinutes: number;
  /** A wall clock on the session's own zone, or an offset when nobody chose an hour. */
  startLabel: string;
  pastPlannedEnd: boolean;
}

export interface RunSheet {
  rows: RunSheetRow[];
  /** How many rows precede the planned-end marker. Null when no budget was set. */
  plannedEndAfter: number | null;
  plannedEndLabel: string | null;
  overrunMinutes: number;
  /** How long the session actually runs, each section timed the way a row is: the single
   *  place this is measured, so a strip and a rail reading it can never disagree. */
  totalMinutes: number;
}

function offsetLabel(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  return `+${hours}:${String(minutes % 60).padStart(2, "0")}`;
}

/**
 * The label a gutter row carries.
 *
 * An occurrence nobody set an hour for reads in offsets rather than in a clock derived from the
 * instant it is stored at: that instant is a midnight in the series' zone, and printing an hour
 * off it would show a time no organizer ever chose, differently to every reader. A timed one is
 * read in the zone that governs it, which is the series' own, so an organizer in another country
 * sees the hour the community will actually meet at.
 */
function labeller(session: Session): (minutes: number) => string {
  if (!session.hasTime) return offsetLabel;
  const zone = governingZone(session);
  const start = new Date(session.scheduledAt).getTime();
  return (minutes) =>
    instantToZoned(new Date(start + minutes * 60_000).toISOString(), zone).time;
}

/** The index the marker sits at: the first section starting at or beyond the budget. */
function markerIndex(starts: number[], budget: number): number {
  const past = starts.findIndex((start) => start >= budget);
  return past === -1 ? starts.length : past;
}

/**
 * The whole agenda placed on a clock, with one planned-end marker rather than a meter per block.
 *
 * The marker falls on a section boundary and never inside a section: a block that merely runs
 * past the budget is still one block, and cutting it in half would put a line where a reader
 * cannot act on it. The overrun is therefore reported as a number on the marker rather than as a
 * position in the list.
 */
export function runSheet(session: Session): RunSheet {
  const label = labeller(session);
  const starts: number[] = [];
  let running = 0;
  for (const section of session.sections) {
    starts.push(running);
    running += sectionRunMinutes(section);
  }

  const budget = session.durationMinutes;
  if (budget === null || budget <= 0) {
    return {
      rows: session.sections.map((section, index) => ({
        section,
        startMinutes: starts[index] ?? 0,
        runMinutes: sectionRunMinutes(section),
        startLabel: label(starts[index] ?? 0),
        pastPlannedEnd: false,
      })),
      plannedEndAfter: null,
      plannedEndLabel: null,
      overrunMinutes: 0,
      totalMinutes: running,
    };
  }

  const marker = markerIndex(starts, budget);
  return {
    rows: session.sections.map((section, index) => ({
      section,
      startMinutes: starts[index] ?? 0,
      runMinutes: sectionRunMinutes(section),
      startLabel: label(starts[index] ?? 0),
      pastPlannedEnd: index >= marker,
    })),
    plannedEndAfter: marker,
    plannedEndLabel: label(budget),
    overrunMinutes: Math.max(0, running - budget),
    totalMinutes: running,
  };
}
