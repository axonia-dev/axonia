import { z } from "zod";

import { meetingPlaceSchema } from "@/features/events/domain/meeting-place";
import { sectionSchema } from "@/features/events/domain/section";
import type { Series } from "@/features/events/domain/series";
import { sessionStatusSchema } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { isOpen } from "@/features/events/domain/slot";

// A schedule entry may be a virtual ghost projected from a series' default
// structure. Its sections carry no persisted identity yet, so id/sessionId
// arrive as null (see backend ScheduleService#toGhost). Real entries still
// populate them — hence nullable here rather than loosening sectionSchema.
const scheduleSectionSchema = sectionSchema.extend({
  id: z.number().nullable(),
  sessionId: z.number().nullable(),
});

const scheduleEntrySchema = z.object({
  sessionId: z.number().nullable(),
  seriesId: z.number().nullable(),
  title: z.string().nullable(),
  occurrenceDate: z.string().nullable(),
  scheduledAt: z.string().nullable(),
  /** Whether anybody chose an hour for this occurrence. A stored fact rather than a shape read off
   *  `scheduledAt`: an untimed occurrence sits at midnight in its series' zone, which is only
   *  midnight UTC when that zone is UTC. */
  hasTime: z.boolean(),
  /** The zone this occurrence's wall clock is read in, null for an entry belonging to no series.
   *
   *  Required rather than defaulted, exactly as it is on a session: a reschedule opened from the
   *  calendar reads it, and a missing key defaulted to null would read every occurrence as
   *  zone-less and compose its new hour on the viewer's clock instead of the community's. The
   *  parse has to fail instead. */
  seriesTimeZone: z.string().nullable(),
  durationMinutes: z.number().nullable(),
  status: sessionStatusSchema,
  virtual: z.boolean(),
  place: meetingPlaceSchema,
  sections: z.array(scheduleSectionSchema),
});

export const scheduleSchema = z.array(scheduleEntrySchema);

export type ScheduleEntry = z.infer<typeof scheduleEntrySchema>;

/** Whether the clock has already reached this occurrence. An entry the projection carries no
 *  instant for cannot be shown to be past, and is treated as still ahead. */
export function hasPassed(
  entry: ScheduleEntry,
  now: Date = new Date(),
): boolean {
  return entry.scheduledAt !== null && new Date(entry.scheduledAt) <= now;
}

/** What to call an entry on screen: its own title, else the title of the series projecting it. */
export function resolveLabel(
  entry: ScheduleEntry,
  seriesList: Series[],
): string {
  if (entry.title !== null) return entry.title;
  const series = seriesList.find((s) => s.id === entry.seriesId);
  return series?.title ?? "Session";
}

export const scheduleActivitySchema = z.array(
  z.string().regex(/^\d{4}-\d{2}$/),
);

/** Every slot of every section, which is what the readings below all count over. */
export function slotsOf(entry: ScheduleEntry): Slot[] {
  return entry.sections.flatMap((s) => s.slots);
}

export function presenterIdsOf(entry: ScheduleEntry): string[] {
  return [...new Set(slotsOf(entry).flatMap((s) => s.presenterUserIds))];
}

export function openSpotLabel(entry: ScheduleEntry): string | null {
  if (entry.virtual || entry.status === "HELD") return null;
  const slots = slotsOf(entry);
  if (slots.length === 0) return "Open for sign-up";
  const count = slots.filter(isOpen).length;
  if (count === 0) return null;
  return count === 1 ? "1 spot open" : `${count} spots open`;
}

/** The first occurrence still ahead: what a reader scanning the month is most likely looking for.
 *  A projection and a session already held are both skipped, on the same rule the timeline marks
 *  its next-up entry by. */
export function nextUpEntry(
  entries: ScheduleEntry[],
  now: Date = new Date(),
): ScheduleEntry | null {
  return (
    entries
      .filter(
        (e) => !e.virtual && e.status === "SCHEDULED" && !hasPassed(e, now),
      )
      .sort((a, b) =>
        (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? ""),
      )[0] ?? null
  );
}

/** The clock time to show beside an entry, or null when nobody chose one. Reads the stored fact
 *  rather than testing the instant: an occurrence with no time is a midnight in its series' zone,
 *  which is only midnight UTC when that zone is UTC, and a session genuinely scheduled at midnight
 *  is a real time that must still be shown. */
export function timeLabel(entry: ScheduleEntry): string | null {
  if (entry.scheduledAt === null || !entry.hasTime) return null;
  return new Date(entry.scheduledAt).toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  });
}
