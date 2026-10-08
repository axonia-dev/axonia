import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import {
  distinctPresenters,
  isCancelled,
  isHeld,
  isSkipped,
  needsCloseAnswer,
  sessionSchema,
  sessionTitle,
  slotCount,
} from "@/features/events/domain/session";

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
  it("parses a place carrying a location, join link and joining details", () => {
    const session = sessionSchema.parse({
      ...raw,
      place: populatedPlace,
      placeOverride: populatedPlace,
      seriesPlace: populatedPlace,
    });
    expect(session.place).toEqual(populatedPlace);
    expect(session.place.joinUrl).toBe("https://meet.example/dojo");
  });
});

describe("session status helpers", () => {
  it("detects held and cancelled", () => {
    expect(isHeld(sessionSchema.parse({ ...raw, status: "HELD" }))).toBe(true);
    expect(
      isCancelled(sessionSchema.parse({ ...raw, status: "CANCELLED" })),
    ).toBe(true);
    expect(isCancelled(sessionSchema.parse(raw))).toBe(false);
  });
});

describe("isSkipped", () => {
  it("is true only for SKIPPED", () => {
    expect(isSkipped(sessionSchema.parse({ ...raw, status: "SKIPPED" }))).toBe(
      true,
    );
    expect(isSkipped(sessionSchema.parse(raw))).toBe(false);
  });
});

describe("needsCloseAnswer", () => {
  const now = new Date("2026-03-10T00:00:00Z");

  it("is true for a scheduled session whose time has passed", () => {
    const past = sessionSchema.parse({
      ...raw,
      scheduledAt: "2026-03-09T10:00:00Z",
    });
    expect(needsCloseAnswer(past, now)).toBe(true);
  });

  it("is false for a scheduled session still in the future", () => {
    const future = sessionSchema.parse({
      ...raw,
      scheduledAt: "2099-01-01T10:00:00Z",
    });
    expect(needsCloseAnswer(future, now)).toBe(false);
  });

  it("is false once a past session has already been held", () => {
    const held = sessionSchema.parse({
      ...raw,
      status: "HELD",
      scheduledAt: "2026-03-09T10:00:00Z",
    });
    expect(needsCloseAnswer(held, now)).toBe(false);
  });
});

describe("sessionTitle", () => {
  it("uses the first titled slot across sections", () => {
    expect(sessionTitle(sessionSchema.parse(raw))).toBe("Pairing on hexagons");
  });

  it("falls back to a generic label when no slot is titled", () => {
    const untitled = { ...section, slots: [{ ...slot, title: null }] };
    expect(
      sessionTitle(sessionSchema.parse({ ...raw, sections: [untitled] })),
    ).toBe("Session");
  });

  it("falls back to a generic label for an unshaped session", () => {
    expect(sessionTitle(sessionSchema.parse({ ...raw, sections: [] }))).toBe(
      "Session",
    );
  });

  it("uses the session's own title before the generic label", () => {
    // A one-off created with a title and nothing slotted yet. The mail a session sends is
    // headed with this exact field, so the page its own button leads to has to agree.
    expect(
      sessionTitle(
        sessionSchema.parse({
          ...raw,
          title: "Inside lead window",
          sections: [],
        }),
      ),
    ).toBe("Inside lead window");
  });

  it("still prefers a titled slot to the session's own title", () => {
    // A series occurrence carries no title of its own, so nothing about its identity moves;
    // where both exist the content is the more useful name.
    expect(
      sessionTitle(
        sessionSchema.parse({ ...raw, title: "Inside lead window" }),
      ),
    ).toBe("Pairing on hexagons");
  });

  it("ignores a blank title rather than heading a page with whitespace", () => {
    expect(
      sessionTitle(sessionSchema.parse({ ...raw, title: "   ", sections: [] })),
    ).toBe("Session");
  });
});

describe("distinctPresenters", () => {
  it("de-duplicates presenter ids across all slots of all sections", () => {
    // given - alice presents twice, bob once
    const second = {
      ...slot,
      id: 2,
      position: 1,
      presenterUserIds: ["bob", "alice"],
    };
    const session = sessionSchema.parse({
      ...raw,
      sections: [{ ...section, slots: [slot, second] }],
    });

    // when
    const actual = distinctPresenters(session);

    // then
    expect(actual).toEqual(["alice", "bob"]);
  });

  it("is empty for an unshaped session", () => {
    // when
    const actual = distinctPresenters(
      sessionSchema.parse({ ...raw, sections: [] }),
    );

    // then
    expect(actual).toEqual([]);
  });
});

describe("slotCount", () => {
  it("counts slots across every section", () => {
    // given
    const second = {
      ...section,
      id: 4,
      position: 1,
      slots: [{ ...slot, id: 2 }],
    };
    const session = sessionSchema.parse({
      ...raw,
      sections: [section, second],
    });

    // when
    const actual = slotCount(session);

    // then
    expect(actual).toBe(2);
  });
});
