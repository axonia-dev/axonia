import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { RescheduleTarget } from "@/features/events/domain/reschedule-target";
import {
  governingZone,
  sameScheduleAs,
  targetFromEntry,
  targetFromSession,
  wallClockOf,
} from "@/features/events/domain/reschedule-target";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { aScheduledSession } from "@/test/session-fixture";

// The suite pins the viewer to America/Los_Angeles, and every zone named here is a different one,
// so a rule that quietly read the viewer's clock could not pass by agreeing with it.

function target(overrides: Partial<RescheduleTarget> = {}): RescheduleTarget {
  return {
    scheduledAt: "2026-08-13T15:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: "alice",
    placeOverride: EMPTY_PLACE,
    seriesPlace: EMPTY_PLACE,
    seriesTimeZone: "Europe/Paris",
    ...overrides,
  };
}

function makeEntry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: null,
    seriesId: 3,
    title: null,
    occurrenceDate: "2026-08-13",
    scheduledAt: "2026-08-13T15:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: true,
    place: EMPTY_PLACE,
    seriesTimeZone: "Europe/Paris",
    sections: [],
    ...overrides,
  };
}

describe("governingZone", () => {
  it("takes the series' zone when there is one", () => {
    expect(governingZone(target({ seriesTimeZone: "Europe/Paris" }))).toBe(
      "Europe/Paris",
    );
  });

  it("falls back to the viewer's zone for a one-off, which has no series", () => {
    expect(governingZone(target({ seriesTimeZone: null }))).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
  });

  /** The one definition serves both read models, which is what stops the rule drifting apart. */
  it("reads a session by the same rule as a target", () => {
    expect(governingZone(aScheduledSession())).toBe("Europe/Paris");
    expect(governingZone(aScheduledSession({ seriesTimeZone: null }))).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
  });
});

describe("wallClockOf", () => {
  it("reads the hour on the governing clock, not the viewer's", () => {
    // 15:00Z is 17:00 in Paris and 08:00 on the pinned viewer clock.
    expect(wallClockOf(target())).toBe("17:00");
  });

  it("answers null for an occurrence nobody chose an hour for", () => {
    expect(wallClockOf(target({ hasTime: false }))).toBeNull();
  });
});

describe("sameScheduleAs", () => {
  /** 15:00Z is 17:00 in Paris. Composed back in Paris it is the same instant; composed on any
   *  other clock it is a session moved by that clock's offset. */
  it("hands an occurrence's hour back on the clock the server composes it in", () => {
    expect(sameScheduleAs(target())).toEqual({
      date: "2026-08-13",
      time: "17:00",
      zone: "Europe/Paris",
    });
  });

  /** The date is the one the occurrence falls on in its own zone, not the one it falls on in UTC:
   *  22:00Z on the 13th is already the 14th in Paris. */
  it("names the day on the governing clock too, not the UTC day", () => {
    expect(
      sameScheduleAs(target({ scheduledAt: "2026-08-13T22:00:00Z" })),
    ).toMatchObject({ date: "2026-08-14", time: "00:00" });
  });

  it("keeps an occurrence nobody chose an hour for untimed", () => {
    expect(sameScheduleAs(target({ hasTime: false })).time).toBeNull();
  });

  /**
   * A session with no series is composed in whatever zone the request states, so UTC round-trips
   * exactly. The viewer's clock would too, most of the time: the hour a fall-back transition
   * repeats occurs twice on it, and composing that hour back can name the other one, moving a
   * session by an hour nobody touched. UTC has no such hour.
   */
  it("states a session with no series in UTC rather than on the viewer's clock", () => {
    expect(sameScheduleAs(target({ seriesTimeZone: null }))).toEqual({
      date: "2026-08-13",
      time: "15:00",
      zone: "UTC",
    });
  });
});

describe("targetFromSession", () => {
  it("takes the session's own override, never the place it resolved to", () => {
    const session = aScheduledSession({
      place: { location: "Room 4B", joinUrl: null, joiningDetails: null },
      placeOverride: EMPTY_PLACE,
      seriesPlace: { location: "Room 4B", joinUrl: null, joiningDetails: null },
    });

    expect(targetFromSession(session).placeOverride).toEqual(EMPTY_PLACE);
    expect(targetFromSession(session).seriesPlace.location).toBe("Room 4B");
  });

  it("carries the facts a full replacement has to hand back unchanged", () => {
    expect(targetFromSession(aScheduledSession())).toMatchObject({
      scheduledAt: "2026-08-15T18:00:00Z",
      hasTime: true,
      durationMinutes: 60,
      facilitatorUserId: "alice",
      seriesTimeZone: "Europe/Paris",
    });
  });
});

describe("targetFromEntry", () => {
  it("takes a ghost's place as its series' standing place, with nothing overriding it", () => {
    const t = targetFromEntry(
      makeEntry({
        place: {
          location: null,
          joinUrl: "https://x",
          joiningDetails: null,
        },
      }),
    );

    expect(t.placeOverride).toEqual(EMPTY_PLACE);
    expect(t.seriesPlace.joinUrl).toBe("https://x");
  });

  it("reads the series' zone off the entry, so a ghost and its session agree", () => {
    expect(governingZone(targetFromEntry(makeEntry()))).toBe("Europe/Paris");
    expect(wallClockOf(targetFromEntry(makeEntry()))).toBe("17:00");
  });

  it("keeps an untimed ghost untimed", () => {
    expect(targetFromEntry(makeEntry({ hasTime: false })).hasTime).toBe(false);
  });
});
