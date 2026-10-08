import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  buildTimeline,
  dateParts,
} from "@/features/events/ui/timeline/timeline";

const TODAY = "2026-01-06T00:00:00.000Z"; // Tuesday

function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
  return {
    sessionId: 1,
    seriesId: 1,
    title: null,
    occurrenceDate: "2026-01-09",
    scheduledAt: "2026-01-09T12:00:00Z",
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    hasTime: true,
    seriesTimeZone: "Europe/Paris",
    ...overrides,
  };
}

describe("dateParts relative labels", () => {
  it("labels the same day as today", () => {
    // when
    const actual = dateParts("2026-01-06T12:00:00Z", TODAY);
    // then
    expect(actual.relative).toBe("today");
  });

  it("labels the next day as tomorrow", () => {
    // when
    const actual = dateParts("2026-01-07T12:00:00Z", TODAY);
    // then
    expect(actual.relative).toBe("tomorrow");
    expect(actual.relativeShort).toBe("tmrw");
  });

  it("labels 2 to 6 days out as in N days", () => {
    // when
    const actual = dateParts("2026-01-09T12:00:00Z", TODAY);
    // then
    expect(actual.relative).toBe("in 3 days");
    expect(actual.relativeShort).toBe("in 3d");
  });

  it("prefers the day label over next week for a nearby cross-week date", () => {
    // given - Mon Jan 12 is in next calendar week but only 6 days out
    // when
    const actual = dateParts("2026-01-12T12:00:00Z", TODAY);
    // then
    expect(actual.relative).toBe("in 6 days");
  });

  it("labels a date 7+ days out in the following calendar week as next week", () => {
    // when
    const actual = dateParts("2026-01-13T12:00:00Z", TODAY);
    // then
    expect(actual.relative).toBe("next week");
    expect(actual.relativeShort).toBe("next wk");
  });

  it("gives no label to a date beyond next week even when under 14 days out", () => {
    // given - Mon Jan 19 is 13 days out but two calendar weeks ahead
    // when
    const actual = dateParts("2026-01-19T12:00:00Z", TODAY);
    // then
    expect(actual.relative).toBeNull();
    expect(actual.relativeShort).toBeNull();
  });

  it("exposes weekday and day number", () => {
    // when
    const actual = dateParts("2026-01-09T12:00:00Z", TODAY);
    // then
    expect(actual.weekdayShort).toBe("Fri");
    expect(actual.day).toBe(9);
  });

  it("renders a date-only iso (ghost occurrenceDate) in UTC, not the local frame", () => {
    // given - a bare date has no time component, so a local-frame render
    // west of UTC would roll it back to the previous day
    // when
    const actual = dateParts("2026-01-09", TODAY);
    // then
    expect(actual.day).toBe(9);
    expect(actual.weekdayShort).toBe("Fri");
  });
});

describe("buildTimeline", () => {
  it("keeps only SCHEDULED and HELD entries, sorted by date", () => {
    // given
    const entries = [
      entry({ sessionId: 3, scheduledAt: "2026-01-20T12:00:00Z" }),
      entry({ sessionId: 2, status: "CANCELLED" }),
      entry({ sessionId: 4, status: "SKIPPED" }),
      entry({ sessionId: 1, scheduledAt: "2026-01-08T12:00:00Z" }),
    ];
    // when
    const actual = buildTimeline(entries, TODAY);
    // then
    const ids = actual.flatMap((i) =>
      i.kind === "entry" ? [i.entry.sessionId] : [],
    );
    expect(ids).toEqual([1, 3]);
  });

  it("marks the first non-held, non-virtual entry as next up", () => {
    // given - a held entry and a ghost precede the first real scheduled one
    const entries = [
      entry({
        sessionId: 1,
        status: "HELD",
        scheduledAt: "2026-01-06T09:00:00Z",
      }),
      entry({
        sessionId: null,
        virtual: true,
        scheduledAt: "2026-01-08T12:00:00Z",
        occurrenceDate: "2026-01-08",
      }),
      entry({ sessionId: 5, scheduledAt: "2026-01-10T12:00:00Z" }),
      entry({ sessionId: 6, scheduledAt: "2026-01-12T12:00:00Z" }),
    ];
    // when
    const actual = buildTimeline(entries, TODAY);
    // then
    const flags = actual.flatMap((i) => (i.kind === "entry" ? [i.nextUp] : []));
    expect(flags).toEqual([false, false, true, false]);
  });

  it("inserts a month marker when the month changes, but never for the current month", () => {
    // given
    const entries = [
      entry({ sessionId: 1, scheduledAt: "2026-01-10T12:00:00Z" }),
      entry({ sessionId: 2, scheduledAt: "2026-02-03T12:00:00Z" }),
    ];
    // when
    const actual = buildTimeline(entries, TODAY);
    // then
    const months = actual.flatMap((i) => (i.kind === "month" ? [i.label] : []));
    expect(months).toEqual(["February"]);
  });

  it("inserts a quiet-stretch gap for 14+ days between consecutive entries", () => {
    // given - Jan 8 to Jan 25 is 17 days
    const entries = [
      entry({ sessionId: 1, scheduledAt: "2026-01-08T12:00:00Z" }),
      entry({ sessionId: 2, scheduledAt: "2026-01-25T12:00:00Z" }),
    ];
    // when
    const actual = buildTimeline(entries, TODAY);
    // then
    const gaps = actual.flatMap((i) => (i.kind === "gap" ? [i.weeks] : []));
    expect(gaps).toEqual([2]);
  });

  it("does not insert a gap under 14 days", () => {
    // given
    const entries = [
      entry({ sessionId: 1, scheduledAt: "2026-01-08T12:00:00Z" }),
      entry({ sessionId: 2, scheduledAt: "2026-01-18T12:00:00Z" }),
    ];
    // when
    const actual = buildTimeline(entries, TODAY);
    // then
    expect(actual.some((i) => i.kind === "gap")).toBe(false);
  });
});
