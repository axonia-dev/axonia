import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Session } from "@/features/events/domain/session";
import { instantToZoned } from "@/features/events/domain/zoned-instant";
import { viewerZone } from "@/lib/zones";

/**
 * Everything a reschedule needs to know about the occurrence it is moving, and nothing else.
 *
 * The two callers that reschedule hold different values: the session page has a whole `Session`,
 * the calendar has a `ScheduleEntry` that may still be a ghost with no row behind it. Both can
 * answer these seven questions, and no editor that answers them needs anything more, so this is
 * what the dialog takes rather than either of the two read models. A dialog written against
 * `Session` could only ever be mounted from one of the two places, which is how the same
 * conversion rule came to be written twice and drift apart.
 */
export interface RescheduleTarget {
  scheduledAt: string;
  /** Whether anybody ever chose an hour. False is an answer, not a gap: an untimed occurrence
   *  keeps its hour open, and the editor has to be able to leave it that way. */
  hasTime: boolean;
  durationMinutes: number | null;
  facilitatorUserId: string | null;
  /** What this one occurrence stores for itself, empty when it inherits. Never the resolved
   *  place: a form seeded from that turns inheritance into an override the moment it is saved. */
  placeOverride: MeetingPlace;
  /** The standing place inheritance falls back to, empty when there is none. */
  seriesPlace: MeetingPlace;
  /** The zone the series composes its occurrences in, null for a session that belongs to none. */
  seriesTimeZone: string | null;
}

/**
 * The zone that governs an occurrence's wall clock: its series' when it has one, the viewer's
 * when it has none.
 *
 * The distinction is the occurrence's own, not a preference. An occurrence of a series is composed
 * by the server in that series' zone whatever a request states, so any hour read or written on
 * another clock names a different instant. A session with no series has no such zone, so the
 * viewer's is the only one there is, and the server honours it.
 *
 * Stated once, for every reader of a governed clock: the session page, the calendar drag and the
 * reschedule dialog all take their zone from here. Two statements of this rule is how a seed and
 * the value it composes back came to disagree by a whole zone offset.
 */
export function governingZone(
  target: Pick<RescheduleTarget, "seriesTimeZone">,
): string {
  return target.seriesTimeZone ?? viewerZone();
}

/** The hour an occurrence meets at, on the clock that governs it, or null when nobody chose one. */
export function wallClockOf(
  target: Pick<RescheduleTarget, "scheduledAt" | "hasTime" | "seriesTimeZone">,
): string | null {
  if (!target.hasTime) return null;
  return instantToZoned(target.scheduledAt, governingZone(target)).time;
}

/**
 * The occurrence's stored schedule said straight back, for a write that means to move everything
 * else and nothing about when it meets.
 *
 * `PUT /sessions/{id}` replaces the whole session, so an editor changing only the length or only
 * the place still has to state a schedule, and the one it states has to name the instant already
 * stored. The backend's release policy depends on it: a reminder nobody touched stays where an
 * organizer put it only while the instant it is handed equals the one it already had.
 *
 * The zone is not {@link governingZone}'s. An occurrence of a series is composed in that series'
 * zone whatever a request states, so its hour has to be read on that same clock or the session
 * moves by the offset between them. A session with no series is composed in the zone it states,
 * and there UTC is the better answer than the viewer's: the hour a fall-back transition repeats
 * happens twice on a local clock, so composing it back can name the other one and move a session
 * by an hour nobody touched. UTC has no repeated hour.
 */
export function sameScheduleAs(
  target: RescheduleTarget,
): UpdateSessionInput["scheduledAt"] {
  const zone = target.seriesTimeZone ?? "UTC";
  const at = instantToZoned(target.scheduledAt, zone);
  return { date: at.date, time: target.hasTime ? at.time : null, zone };
}

export function targetFromSession(session: Session): RescheduleTarget {
  return {
    scheduledAt: session.scheduledAt,
    hasTime: session.hasTime,
    durationMinutes: session.durationMinutes,
    facilitatorUserId: session.facilitatorUserId,
    placeOverride: session.placeOverride,
    seriesPlace: session.seriesPlace,
    seriesTimeZone: session.seriesTimeZone,
  };
}

/**
 * A ghost as something to reschedule. GHOSTS ONLY: an entry with a `sessionId` must never be
 * passed here. Read its session with the gateway and go through {@link targetFromSession} instead,
 * which is what the calendar drag already does.
 *
 * The reason is that `PUT /sessions/{id}` replaces the whole session, so every field this target
 * omits is a field the confirm erases, and two of them are only safe to guess for an occurrence
 * with no row behind it:
 *
 * - `placeOverride` is empty, because a ghost has no row to hold an override and its place is its
 *   series' by construction. A real entry's `place` is the RESOLVED one, so a projection cannot
 *   tell an override from an inherited place: seeded from it, a real occurrence that overrode
 *   where it meets would have that override silently cleared, or the series' own place posted
 *   back as this occurrence's, detaching it for good.
 * - `facilitatorUserId` is null, because the schedule projection does not carry it at all. A
 *   ghost genuinely has none, and a materialised occurrence starts life with none. A real entry
 *   may well have one, and confirming this target would wipe it with nothing on screen saying so.
 *
 * The instant falls back to midnight on the entry's own anchor only for an entry the projection
 * never emits: one placed on the calendar with no instant at all.
 */
export function targetFromEntry(entry: ScheduleEntry): RescheduleTarget {
  return {
    scheduledAt: entry.scheduledAt ?? `${entry.occurrenceDate ?? ""}T00:00:00Z`,
    hasTime: entry.hasTime,
    durationMinutes: entry.durationMinutes,
    facilitatorUserId: null,
    placeOverride: EMPTY_PLACE,
    seriesPlace: entry.place,
    seriesTimeZone: entry.seriesTimeZone,
  };
}
