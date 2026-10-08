import { governingZone } from "@/features/events/domain/reschedule-target";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { instantToZoned } from "@/features/events/domain/zoned-instant";

/**
 * The calendar day an occurrence falls on: the day its instant lands on, read on the clock that
 * governs it. Stated once, for the grid cell a chip is drawn in, the day a panel and a menu name,
 * and the row a timeline dates - four readings of one question that must never disagree.
 *
 * It is not `occurrenceDate`. That column is the occurrence's anchor in its series' cadence, and a
 * reschedule deliberately leaves it where it was: the anchor is what suppresses the ghost for the
 * week the meeting moved out of, so following the move would re-project a session an organizer
 * already placed elsewhere. Read as a day to show, it strands every rescheduled occurrence in the
 * cell it left.
 *
 * Nor is it the UTC date of the instant. An occurrence nobody chose an hour for is midnight in its
 * series' zone, which is the evening before in UTC for every zone east of it, so a UTC reading
 * dates a whole series a day early. The two rules are each right about one of these and wrong
 * about the other, which is how the calendar and the timeline came to state different days for
 * the same session.
 *
 * The anchor is the answer only for an entry carrying no instant at all, and an entry carrying
 * neither has no day to be shown on.
 */
export function entryDayIso(entry: ScheduleEntry): string {
  if (entry.scheduledAt !== null) {
    return instantToZoned(entry.scheduledAt, governingZone(entry)).date;
  }
  return entry.occurrenceDate ?? "";
}
