import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import {
  openSpotLabel,
  presenterIdsOf,
  resolveLabel,
  scheduleSchema,
  timeLabel,
} from "@/features/events/domain/schedule";
import type { Slot } from "@/features/events/domain/slot";
import { aScheduleEntry } from "@/test/session-fixture";

function slot(overrides: Partial<Slot> = {}): Slot {
  return {
    id: 1,
    sectionId: 1,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: "A talk",
    notes: null,
    durationMinutes: 20,
    tags: [],
    ...overrides,
  };
}

function section(slots: Slot[]) {
  return {
    id: 1,
    sessionId: 1,
    position: 0,
    sectionTypeId: 1,
    sectionType: "Talks",
    sectionTypeIcon: "mic",
    sectionTypeAccentColor: "#ff5c97",
    title: null,
    durationMinutes: 40,
    slots,
  };
}

const realEntry = {
  sessionId: 9,
  seriesId: 1,
  title: "Frontend Dojo #12",
  occurrenceDate: "2026-06-19",
  scheduledAt: "2026-06-19T18:00:00Z",
  hasTime: true,
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  status: "SCHEDULED",
  virtual: false,
  place: EMPTY_PLACE,
  sections: [],
};

const ghostEntry = {
  sessionId: null,
  seriesId: 1,
  title: null,
  occurrenceDate: "2026-06-26",
  scheduledAt: "2026-06-26T18:00:00Z",
  hasTime: true,
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  status: "SCHEDULED",
  virtual: true,
  place: EMPTY_PLACE,
  sections: [],
};

// A ghost carries its series' projected default structure: sections without a
// persisted identity (id/sessionId are null), exactly as ScheduleService#toGhost emits.
const ghostWithProjectedSections = {
  ...ghostEntry,
  sections: [
    {
      id: null,
      sessionId: null,
      position: 0,
      sectionTypeId: 5,
      sectionType: "Lightning talks",
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: "Talks",
      durationMinutes: 30,
      slots: [],
    },
  ],
};

const populatedPlace = {
  location: "Room 4B",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "ID 391 2244",
};

describe("scheduleSchema", () => {
  it("parses a real entry (virtual:false with sessionId)", () => {
    const [entry] = scheduleSchema.parse([realEntry]);
    expect(entry?.sessionId).toBe(9);
    expect(entry?.virtual).toBe(false);
    expect(entry?.title).toBe("Frontend Dojo #12");
    expect(entry?.occurrenceDate).toBe("2026-06-19");
  });

  it("parses a ghost entry (virtual:true, sessionId:null)", () => {
    const [entry] = scheduleSchema.parse([ghostEntry]);
    expect(entry?.sessionId).toBeNull();
    expect(entry?.virtual).toBe(true);
    expect(entry?.title).toBeNull();
  });

  it("parses a ghost entry whose projected sections have null id/sessionId", () => {
    const [entry] = scheduleSchema.parse([ghostWithProjectedSections]);
    expect(entry?.sections).toHaveLength(1);
    expect(entry?.sections[0]?.id).toBeNull();
    expect(entry?.sections[0]?.sessionId).toBeNull();
    expect(entry?.sections[0]?.slots).toEqual([]);
  });

  it("parses a SKIPPED entry", () => {
    const [entry] = scheduleSchema.parse([
      { ...realEntry, status: "SKIPPED", sessionId: 11 },
    ]);
    expect(entry?.status).toBe("SKIPPED");
  });

  /**
   * Required rather than defaulted, because a write path reads it. A missing key quietly defaulted
   * to null would read every occurrence as belonging to no series and compose its new hour on the
   * viewer's clock, which is the whole defect this field exists to close.
   */
  it("refuses an entry that never says which clock its hour is read on", () => {
    const withoutZone: Record<string, unknown> = { ...realEntry };
    delete withoutZone.seriesTimeZone;

    expect(scheduleSchema.safeParse([withoutZone]).success).toBe(false);
  });

  it("parses a mixed array of real and ghost entries", () => {
    const entries = scheduleSchema.parse([realEntry, ghostEntry]);
    expect(entries).toHaveLength(2);
  });

  it("parses a place carrying a location, join link and joining details", () => {
    const [entry] = scheduleSchema.parse([
      { ...realEntry, place: populatedPlace },
    ]);
    expect(entry?.place).toEqual(populatedPlace);
    expect(entry?.place.joinUrl).toBe("https://meet.example/dojo");
  });
});

const seriesList = [{ id: 1, title: "Frontend Dojo" }] as never;

describe("resolveLabel", () => {
  it("prefers the entry's own title", () => {
    expect(resolveLabel(aScheduleEntry({ title: "Kickoff" }), seriesList)).toBe(
      "Kickoff",
    );
  });

  it("falls back to the series it belongs to", () => {
    expect(resolveLabel(aScheduleEntry({ seriesId: 1 }), seriesList)).toBe(
      "Frontend Dojo",
    );
  });

  it("names an entry belonging to neither", () => {
    expect(resolveLabel(aScheduleEntry({ seriesId: 99 }), seriesList)).toBe(
      "Session",
    );
  });
});

describe("entry readings", () => {
  it("reads the clock time off a timed entry", () => {
    expect(
      timeLabel(
        aScheduleEntry({ scheduledAt: "2026-08-20T12:00:00Z", hasTime: true }),
      ),
    ).toMatch(/^\d{2}:\d{2}$/);
  });

  it("gives no clock time to an entry nobody set an hour for, even off UTC midnight", () => {
    expect(
      timeLabel(
        aScheduleEntry({
          scheduledAt: "2026-08-19T22:00:00Z",
          hasTime: false,
        }),
      ),
    ).toBeNull();
  });

  it("counts the slots nobody has taken", () => {
    const withSlots = aScheduleEntry({
      sections: [
        section([
          slot({ presenterUserIds: [] }),
          slot({ presenterUserIds: ["u1"] }),
        ]),
      ],
    });
    expect(openSpotLabel(withSlots)).toBe("1 spot open");
  });

  it("calls a session with no slots at all open for sign-up", () => {
    expect(openSpotLabel(aScheduleEntry({ sections: [] }))).toBe(
      "Open for sign-up",
    );
  });

  it("says nothing about spots on a projected or held entry", () => {
    expect(
      openSpotLabel(aScheduleEntry({ virtual: true, sections: [] })),
    ).toBeNull();
    expect(
      openSpotLabel(aScheduleEntry({ status: "HELD", sections: [] })),
    ).toBeNull();
  });

  it("collects each presenter once across every section", () => {
    const twoSections = aScheduleEntry({
      sections: [
        section([slot({ presenterUserIds: ["u1", "u2"] })]),
        section([slot({ presenterUserIds: ["u2"] })]),
      ],
    });
    expect(presenterIdsOf(twoSections)).toEqual(["u1", "u2"]);
  });

  it("dedupes presenter ids within a single section too", () => {
    const oneSection = aScheduleEntry({
      sections: [
        section([
          slot({ presenterUserIds: ["u1"] }),
          slot({ presenterUserIds: ["u2", "u1"] }),
        ]),
      ],
    });
    expect(presenterIdsOf(oneSection)).toEqual(["u1", "u2"]);
  });

  it("pluralizes the open-spot label once more than one spot is open", () => {
    const twoOpen = aScheduleEntry({
      sections: [
        section([
          slot({ presenterUserIds: [] }),
          slot({ presenterUserIds: [] }),
          slot({ presenterUserIds: ["u1"] }),
        ]),
      ],
    });
    expect(openSpotLabel(twoOpen)).toBe("2 spots open");
  });

  it("says nothing about spots once every slot is taken", () => {
    const allFilled = aScheduleEntry({
      sections: [section([slot({ presenterUserIds: ["u1"] })])],
    });
    expect(openSpotLabel(allFilled)).toBeNull();
  });

  it("reads a stored hour that happens to land on UTC midnight, and drops an unstored one at the same instant", () => {
    // Midnight UTC is a real hour when somebody chose one, and no hour when nobody did. The
    // instant is the same either way, which is why the stored fact decides and not its shape.
    expect(
      timeLabel(
        aScheduleEntry({ scheduledAt: "2026-01-09T00:00:00Z", hasTime: true }),
      ),
    ).not.toBeNull();
    expect(
      timeLabel(
        aScheduleEntry({
          scheduledAt: "2026-01-09T00:00:00Z",
          hasTime: false,
        }),
      ),
    ).toBeNull();
  });

  it("gives no clock time to an entry with no instant at all", () => {
    expect(timeLabel(aScheduleEntry({ scheduledAt: null }))).toBeNull();
  });
});
