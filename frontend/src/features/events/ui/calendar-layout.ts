import type { CSSProperties } from "react";

/**
 * The calendar's own width, centred in whatever room the page gives it. The controls above it, the
 * month nav, the weekday row and the grid all take it, so the view reads as one block rather than a
 * grid with a header floating off to an edge it never reaches.
 */
export const CALENDAR_BLOCK = "mx-auto w-full max-w-[1200px]";

/** The timeline is one column of cards and gains nothing from the calendar's room, so it and the
 *  controls above it keep a reading width, centred in the same page. */
export const TIMELINE_BLOCK = "mx-auto w-full max-w-6xl";

/**
 * How many week rows the shown month has, published to the grid's own cells so each can work out
 * how tall it may be. February can have four, most months five, and a month that starts on a Sunday
 * six: dividing the window by a fixed six would leave a third of a short month as empty air.
 *
 * The reading half of this lives in `DayCell`, which is where a cell's minimum height is set.
 */
export function weekRowsVar(cellCount: number): CSSProperties {
  return { "--calendar-rows": String(cellCount / 7) } as CSSProperties;
}

/** Half the grid, plus the gutter between it and the panel. */
const HALF_PLUS_GUTTER = 616;
const PANEL_WIDTH = 384;

/**
 * Where the session panel stands, measured against the full width the page gives the calendar,
 * which is wider than the calendar itself.
 *
 * The first term puts the panel hard against the grid's right edge with a gutter. The second keeps
 * it inside the page. `min` takes whichever is further left, so a page with room stands the panel
 * beside the grid and a narrower one lets it overlap the last columns rather than run off the edge
 * - with no breakpoint needed to tell the two apart, and no width at which the page scrolls
 * sideways.
 */
export const PANEL_LEFT = `min(calc(50% + ${HALF_PLUS_GUTTER}px), calc(100% - ${PANEL_WIDTH}px))`;
