import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";

/** A place with all three parts filled in, for the tests that need a real one rather than a
 *  particular one. Shared so a case exercising the whole place cannot quietly become a case
 *  exercising only the link half of it. */
export const A_PLACE: MeetingPlace = {
  location: "Room 4B, Gasperich",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "Passcode 4242",
};

/**
 * A scheduled session with everything a test has not asked about left at its quietest value: no
 * place anywhere, no release intent, no sections. Overrides carry the one fact under test, so a
 * reader can tell what the case is about from the call rather than from a wall of fields.
 *
 * It meets at a real hour, so `hasTime` is true; a case about an occurrence nobody set an hour for
 * says so by overriding it. It belongs to a series, so it has a series zone rather than the null a
 * one-off carries, and that zone is deliberately neither UTC nor the zone the suite pins the
 * viewer to: a test that starts reading it cannot then pass by agreeing with either by accident.
 */
export function aScheduledSession(overrides: Partial<Session> = {}): Session {
  return {
    id: 10,
    seriesId: 3,
    communityId: 1,
    status: "SCHEDULED",
    title: null,
    occurrenceDate: "2026-08-15",
    scheduledAt: "2026-08-15T18:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: "alice",
    attendeeCount: 0,
    notes: null,
    recordingUrl: null,
    place: EMPTY_PLACE,
    placeOverride: EMPTY_PLACE,
    seriesPlace: EMPTY_PLACE,
    seriesTimeZone: "Europe/Paris",
    sections: [],
    createdAt: "x",
    updatedAt: "x",
    closedAt: null,
    wrapUpReleaseAt: null,
    wrapUpStatus: null,
    upcomingReleaseAt: null,
    upcomingStatus: null,
    ...overrides,
  };
}

/**
 * The same occurrence as the calendar projects it: a scheduled chip with a real row behind it, an
 * hour, and a series whose zone is again neither UTC nor the viewer's.
 *
 * `place` is the RESOLVED place, which is all the projection carries, so no entry built here can
 * say whether the occurrence overrides where it meets or inherits it. A case that turns on that
 * difference has to read the session, and says so by overriding `sessionId` or by pairing this
 * with an `aScheduledSession`.
 */
export function aScheduleEntry(
  overrides: Partial<ScheduleEntry> = {},
): ScheduleEntry {
  return {
    sessionId: 7,
    seriesId: 1,
    title: null,
    occurrenceDate: "2026-07-02",
    scheduledAt: "2026-07-02T18:00:00Z",
    hasTime: true,
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

/**
 * A filled talk with one presenter and nothing else said about it. Every session test used to
 * rebuild this inline, which is how the same nine fields came to be written a dozen times with
 * no single place to add the tenth.
 */
export function aSlot(overrides: Partial<Slot> = {}): Slot {
  return {
    id: 7,
    sectionId: 3,
    position: 0,
    presenterUserIds: ["bob"],
    topicId: null,
    title: "Pairing on hexagons",
    notes: null,
    durationMinutes: 20,
    tags: [],
    ...overrides,
  };
}

/** A section holding one talk. Pass `slots: []` for the round-table case, which is legitimate. */
export function aSection(overrides: Partial<Section> = {}): Section {
  return {
    id: 3,
    sessionId: 10,
    position: 0,
    sectionTypeId: 2,
    sectionType: "Deep Dive",
    sectionTypeIcon: null,
    sectionTypeAccentColor: null,
    title: null,
    durationMinutes: 30,
    slots: [aSlot()],
    ...overrides,
  };
}
