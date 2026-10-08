import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { sessionSchema } from "@/features/events/domain/session";

const slot = {
  id: 1,
  sectionId: 3,
  position: 0,
  presenterUserIds: ["alice"],
  topicId: null,
  title: "Pairing on hexagons",
  notes: null,
  durationMinutes: 20,
  tags: [],
};

const section = {
  id: 3,
  sessionId: 9,
  position: 0,
  sectionTypeId: 2,
  sectionType: "Talk",
  sectionTypeIcon: null,
  sectionTypeAccentColor: null,
  title: "Main talk",
  durationMinutes: 30,
  slots: [slot],
};

const raw = {
  id: 9,
  seriesId: 1,
  communityId: 1,
  status: "SCHEDULED",
  title: null,
  occurrenceDate: null,
  scheduledAt: "2099-01-01T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: null,
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  place: EMPTY_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: EMPTY_PLACE,
  seriesTimeZone: "Europe/Paris",
  sections: [section],
  createdAt: "2026-06-03T10:00:00Z",
  updatedAt: "2026-06-03T10:00:00Z",
};

const populatedPlace = {
  location: "Room 4B",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "ID 391 2244",
};

describe("sessionSchema", () => {
  it("parses a session with a null facilitator and nested sections", () => {
    const session = sessionSchema.parse(raw);
    expect(session.facilitatorUserId).toBeNull();
    expect(session.sections).toHaveLength(1);
    expect(session.sections[0]?.slots[0]?.presenterUserIds).toEqual(["alice"]);
  });

  /**
   * `community_session.notes` and `recording_url` are NOT NULL DEFAULT '', so a session nobody
   * followed up arrives with empty strings where every other optional text column sends null.
   * Left as "", a surface asking `recordingUrl !== null` draws a link to nowhere.
   */
  it("reads a blank note or recording as the absence it is", () => {
    const session = sessionSchema.parse({
      ...raw,
      notes: "",
      recordingUrl: "   ",
    });

    expect(session.notes).toBeNull();
    expect(session.recordingUrl).toBeNull();
  });

  it("keeps a note and a recording the session really carries", () => {
    const session = sessionSchema.parse({
      ...raw,
      notes: "The p95 numbers landed.",
      recordingUrl: "https://meet.example.com/rec-92",
    });

    expect(session.notes).toBe("The p95 numbers landed.");
    expect(session.recordingUrl).toBe("https://meet.example.com/rec-92");
  });

  it("parses title, occurrenceDate, and SKIPPED status", () => {
    const session = sessionSchema.parse({
      ...raw,
      title: "Architecture deep-dive",
      occurrenceDate: "2026-06-19",
      status: "SKIPPED",
      sections: [],
    });
    expect(session.title).toBe("Architecture deep-dive");
    expect(session.occurrenceDate).toBe("2026-06-19");
    expect(session.status).toBe("SKIPPED");
  });

  it("defaults closedAt and wrapUpReleaseAt to null when absent", () => {
    const session = sessionSchema.parse(raw);
    expect(session.closedAt).toBeNull();
    expect(session.wrapUpReleaseAt).toBeNull();
  });

  it("parses a held session's closedAt and wrapUpReleaseAt", () => {
    const session = sessionSchema.parse({
      ...raw,
      status: "HELD",
      closedAt: "2026-03-09T12:00:00Z",
      wrapUpReleaseAt: "2026-03-11T12:00:00Z",
    });
    expect(session.closedAt).toBe("2026-03-09T12:00:00Z");
    expect(session.wrapUpReleaseAt).toBe("2026-03-11T12:00:00Z");
  });

  it("defaults wrapUpStatus to null when absent - every projection but the detail read", () => {
    const session = sessionSchema.parse(raw);
    expect(session.wrapUpStatus).toBeNull();
  });

  it("parses wrapUpStatus on the single-session detail read", () => {
    const session = sessionSchema.parse({
      ...raw,
      status: "HELD",
      wrapUpStatus: {
        state: "SENT",
        recipientCount: 14,
        sent: 12,
        couldNotReach: 2,
      },
    });
    expect(session.wrapUpStatus).toEqual({
      state: "SENT",
      recipientCount: 14,
      sent: 12,
      couldNotReach: 2,
    });
  });

  it("defaults the reminder's release intent and status to null when absent", () => {
    const session = sessionSchema.parse(raw);
    expect(session.upcomingReleaseAt).toBeNull();
    expect(session.upcomingStatus).toBeNull();
  });

  it("parses the reminder's own release intent and status, beside the wrap-up's", () => {
    const session = sessionSchema.parse({
      ...raw,
      upcomingReleaseAt: "2026-03-08T09:00:00Z",
      upcomingStatus: {
        state: "SCHEDULED",
        recipientCount: 14,
        sent: 0,
        couldNotReach: 0,
      },
    });
    expect(session.upcomingReleaseAt).toBe("2026-03-08T09:00:00Z");
    expect(session.upcomingStatus?.state).toBe("SCHEDULED");
  });

  it("parses a place carrying a location, join link and joining details", () => {
    const session = sessionSchema.parse({
      ...raw,
      place: populatedPlace,
      placeOverride: populatedPlace,
      seriesPlace: populatedPlace,
    });
    expect(session.place).toEqual(populatedPlace);
    expect(session.place.joinUrl).toBe("https://meet.example/dojo");
    expect(session.placeOverride).toEqual(populatedPlace);
    expect(session.seriesPlace).toEqual(populatedPlace);
  });
});
