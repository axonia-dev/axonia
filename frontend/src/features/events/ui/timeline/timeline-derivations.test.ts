import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Slot } from "@/features/events/domain/slot";
import {
  accessibleName,
  entryKey,
  entrySortKey,
  occurrenceHref,
  sneakPeek,
} from "@/features/events/ui/timeline/timeline";

function slot(overrides: Partial<Slot>): Slot {
  return {
    id: 1,
    sectionId: 1,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: null,
    notes: null,
    durationMinutes: 15,
    tags: [],
    ...overrides,
  };
}

function section(slots: Slot[]): ScheduleEntry["sections"][number] {
  return {
    id: 1,
    sessionId: 1,
    position: 0,
    sectionTypeId: null,
    sectionType: null,
    sectionTypeIcon: null,
    sectionTypeAccentColor: null,
    title: null,
    durationMinutes: null,
    slots,
  };
}

function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
    occurrenceDate: "2026-01-09",
    scheduledAt: "2026-01-09T12:00:00Z",
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

describe("slot derivations", () => {
  const filled = slot({
    id: 1,
    title: "Signals in React 19",
    presenterUserIds: ["u1"],
  });
  const filledToo = slot({
    id: 2,
    title: "Vite plugins",
    presenterUserIds: ["u2", "u1"],
  });
  const open = slot({ id: 3 });

  it("sneak peek takes the first filled titled slot and counts the rest", () => {
    // when
    const actual = sneakPeek(
      entry({ sections: [section([filled, filledToo, open])] }),
    );
    // then
    expect(actual).toEqual({
      title: "Signals in React 19",
      presenterId: "u1",
      more: 1,
    });
  });

  it("sneak peek is null without a filled titled slot", () => {
    // when
    const actual = sneakPeek(entry({ sections: [section([open])] }));
    // then
    expect(actual).toBeNull();
  });
});

describe("labels and links", () => {
  it("links real sessions to the session page and ghosts to the occurrence preview", () => {
    // when / then
    expect(occurrenceHref(entry({ sessionId: 7 }), "guild")).toBe(
      "/communities/guild/sessions/7",
    );
    expect(
      occurrenceHref(
        entry({ sessionId: null, occurrenceDate: "2026-01-09" }),
        "guild",
      ),
    ).toBe("/communities/guild/series/1/occurrences/2026-01-09");
  });

  it("keys real sessions by id and ghosts by series and date", () => {
    // when / then
    expect(entryKey(entry({ sessionId: 7 }), 0)).toBe("7");
    expect(entryKey(entry({ sessionId: null }), 0)).toBe("1-2026-01-09");
    expect(
      entryKey(
        entry({ sessionId: null, seriesId: null, occurrenceDate: null }),
        4,
      ),
    ).toBe("x-4");
  });

  it("orders by the instant, falling back to the bare anchor", () => {
    // when / then
    expect(entrySortKey(entry({}))).toBe("2026-01-09T12:00:00Z");
    expect(entrySortKey(entry({ scheduledAt: null }))).toBe("2026-01-09");
    expect(
      entrySortKey(entry({ scheduledAt: null, occurrenceDate: null })),
    ).toBe("");
  });

  it("accessible name carries label, date, spots, and status", () => {
    // when
    const actual = accessibleName(entry({ virtual: true }), "Frontend Dojo");
    // then
    expect(actual).toContain("Frontend Dojo");
    expect(actual).toContain("January 9");
    expect(actual).toContain("projected");
  });

  it("accessible name marks held sessions and open spots", () => {
    // when / then
    expect(accessibleName(entry({ status: "HELD" }), "Dojo")).toContain("held");
    expect(accessibleName(entry({}), "Dojo")).toContain("Open for sign-up");
  });
});
