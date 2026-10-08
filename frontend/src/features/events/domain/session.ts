import { z } from "zod";

import { meetingPlaceSchema } from "@/features/events/domain/meeting-place";
import { sectionSchema } from "@/features/events/domain/section";

/**
 * A text column whose blank spelling means "never set".
 *
 * `community_session.notes` and `recording_url` are NOT NULL DEFAULT '', unlike every other
 * optional text a session carries - V58 says so about `recording_url` in as many words. Without
 * this, `null` never arrives, so a surface asking whether there is a recording gets "yes" for
 * every session ever held and draws a link to nowhere.
 */
const blankAsAbsent = z
  .string()
  .nullable()
  .transform((value) => (value === null || value.trim() === "" ? null : value));

export const sessionStatusSchema = z.enum([
  "SCHEDULED",
  "HELD",
  "CANCELLED",
  "SKIPPED",
]);

export type SessionStatus = z.infer<typeof sessionStatusSchema>;

/** The four states a session's release-controlled mail can honestly be in - see the
 *  backend's `ReleaseState` for the full reasoning behind each. */
const releaseStateSchema = z.enum(["OFF", "SCHEDULED", "QUEUED", "SENT"]);

export type ReleaseState = z.infer<typeof releaseStateSchema>;

/**
 * Populated only on the single-session detail read - every list or schedule projection
 * sends `wrapUpStatus` and `upcomingStatus` null because deriving either costs an extra
 * query. A `null` here means "this isn't the detail read", never a state of its own; the
 * `ReleaseCard` that renders this must treat it that way rather than guessing a state from
 * the null.
 */
const releaseStatusSchema = z.object({
  state: releaseStateSchema,
  recipientCount: z.number(),
  sent: z.number(),
  couldNotReach: z.number(),
});

export type ReleaseStatus = z.infer<typeof releaseStatusSchema>;

export const sessionSchema = z.object({
  id: z.number(),
  seriesId: z.number().nullable(),
  communityId: z.number(),
  status: sessionStatusSchema,
  title: z.string().nullable().default(null),
  occurrenceDate: z.string().nullable().default(null),
  scheduledAt: z.string(),
  /** Whether anybody ever chose an hour for this occurrence. A stored fact rather than a shape read
   *  off `scheduledAt`: an untimed occurrence sits at midnight in its series' zone, which is
   *  indistinguishable from a series that genuinely meets at midnight. */
  hasTime: z.boolean(),
  durationMinutes: z.number().nullable(),
  facilitatorUserId: z.string().nullable(),
  attendeeCount: z.number(),
  notes: blankAsAbsent,
  recordingUrl: blankAsAbsent,
  /** Where this session meets, already resolved: its own place when it names any part of one,
   *  otherwise the standing place of its series. What a join control and a location line read. */
  place: meetingPlaceSchema,
  /** The place this one occurrence sets for itself, empty when it inherits. The fact an edit form
   *  needs and `place` cannot give it: a form seeded from the resolved value turns inheritance
   *  into an override the moment it is saved. */
  placeOverride: meetingPlaceSchema,
  /** The standing place of the session's series, empty when there is none to fall back to. */
  seriesPlace: meetingPlaceSchema,
  /** The zone that governs this session's wall clock, null for a one-off, which belongs to no
   *  series and so has none. The null is the answer and not a gap: it is what tells a reader
   *  whether a community chose the zone an hour is read in or whether the viewer's own is all
   *  there is to go on.
   *
   *  Required rather than defaulted, unlike the optional session fields around it, because a write
   *  path reads it. A missing key defaulted to null would read every series session as zone-less
   *  and silently reschedule it on the viewer's clock, which is the defect this field exists to
   *  close - so the parse must fail instead. */
  seriesTimeZone: z.string().nullable(),
  sections: z.array(sectionSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
  closedAt: z.string().nullable().default(null),
  wrapUpReleaseAt: z.string().nullable().default(null),
  wrapUpStatus: releaseStatusSchema.nullable().default(null),
  /** The reminder's stored release intent. Null is "this session sends no reminder", a
   *  decision in its own right, not a missing value. */
  upcomingReleaseAt: z.string().nullable().default(null),
  upcomingStatus: releaseStatusSchema.nullable().default(null),
});

export type Session = z.infer<typeof sessionSchema>;

export function isHeld(session: Session): boolean {
  return session.status === "HELD";
}

export function isCancelled(session: Session): boolean {
  return session.status === "CANCELLED";
}

export function isSkipped(session: Session): boolean {
  return session.status === "SKIPPED";
}

/** Whether the clock has already reached this session's hour, whatever became of it. */
export function hasPassed(session: Session, now: Date = new Date()): boolean {
  return new Date(session.scheduledAt) <= now;
}

/**
 * A scheduled session whose time has passed and nobody has yet said whether it happened.
 * This is what the session page asks "Did this happen?" about.
 */
export function needsCloseAnswer(
  session: Session,
  now: Date = new Date(),
): boolean {
  return session.status === "SCHEDULED" && hasPassed(session, now);
}

/**
 * When this session's own window closes, or null when no end is knowable: a session with no
 * recorded length has none, and inventing a default would be a guess dressed up as a fact.
 */
function endOf(session: Session): number | null {
  if (!session.hasTime || session.durationMinutes === null) return null;
  return (
    new Date(session.scheduledAt).getTime() + session.durationMinutes * 60_000
  );
}

/**
 * Whether the session is still inside its own scheduled window.
 *
 * The end is exclusive: at the instant the window closes the session stops reading as running and
 * starts owing an answer, the same convention the run sheet's planned-end marker uses for a
 * section that starts exactly on the budget.
 */
export function isRunning(session: Session, now: Date = new Date()): boolean {
  const end = endOf(session);
  return end !== null && hasPassed(session, now) && now.getTime() < end;
}

/**
 * The next instant this session's own reading turns: its start, then its end. A page that states
 * what a session is doing right now registers this rather than polling, so it stops saying
 * "starting soon" the moment the session starts and stops saying "running now" the moment it
 * finishes, without being reloaded.
 *
 * Null once nothing is left to turn on, which includes a session already settled and one whose
 * end nobody can know.
 */
export function nextSessionMark(
  session: Session,
  now: Date = new Date(),
): number | null {
  if (session.status !== "SCHEDULED") return null;
  const marks = [new Date(session.scheduledAt).getTime(), endOf(session)];
  return marks.find((mark) => mark !== null && mark > now.getTime()) ?? null;
}

/**
 * What to call a session on screen, in the order a reader would recognise it.
 *
 * A session's content lives in its sections' slots, so the first titled slot wins: for a series
 * occurrence that is the only identity it has, and nothing about those changes here.
 *
 * A one-off session created with a title of its own comes next. Skipping it was a real defect
 * rather than a simplification: the backend's `SessionFactsAssembler` reads that title directly,
 * so a session mail could be headed with it while the page its own button led to said
 * "Session". The generic label is the last resort, for a session that is neither shaped nor
 * named.
 */
export function sessionTitle(session: Session): string {
  for (const section of session.sections) {
    const titled = section.slots.find(
      (slot) => slot.title !== null && slot.title.trim() !== "",
    );
    if (titled?.title) {
      return titled.title;
    }
  }
  if (session.title !== null && session.title.trim() !== "") {
    return session.title;
  }
  return "Session";
}

/** Distinct presenter ids across every slot of the session, in first-appearance order. */
export function distinctPresenters(session: Session): string[] {
  const seen = new Set<string>();
  for (const section of session.sections) {
    for (const slot of section.slots) {
      for (const userId of slot.presenterUserIds) {
        seen.add(userId);
      }
    }
  }
  return [...seen];
}

/** Number of content slots across the session's sections (the "topics" of the session). */
export function slotCount(session: Session): number {
  return session.sections.reduce(
    (sum, section) => sum + section.slots.length,
    0,
  );
}
