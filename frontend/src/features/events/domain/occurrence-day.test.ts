import { describe, expect, it } from "vitest";

import { entryDayIso } from "@/features/events/domain/occurrence-day";
import { aScheduleEntry } from "@/test/session-fixture";

/** A Wednesday occurrence of a weekly series, on a clock two hours ahead of UTC. */
function anchored() {
  return aScheduleEntry({
    occurrenceDate: "2026-09-02",
    scheduledAt: "2026-09-02T08:00:00Z",
  });
}

describe("entryDayIso", () => {
  it("follows a rescheduled occurrence off its cadence anchor", () => {
    // given an occurrence an organizer moved to the Friday
    const moved = aScheduleEntry({
      ...anchored(),
      scheduledAt: "2026-09-04T08:00:00Z",
    });
    // when / then the anchor it keeps for its series is not the day it meets
    expect(entryDayIso(moved)).toBe("2026-09-04");
  });

  it("reads the instant on the series' clock rather than on UTC", () => {
    // given an untimed occurrence: midnight in Paris, the evening before in UTC
    const untimed = aScheduleEntry({
      occurrenceDate: "2026-09-03",
      scheduledAt: "2026-09-02T22:00:00Z",
      hasTime: false,
    });
    // when / then
    expect(entryDayIso(untimed)).toBe("2026-09-03");
  });

  it("reads a session belonging to no series on the viewer's clock", () => {
    // given the suite pins the viewer to America/Los_Angeles
    const oneOff = aScheduleEntry({
      seriesId: null,
      seriesTimeZone: null,
      occurrenceDate: null,
      scheduledAt: "2026-09-04T02:00:00Z",
    });
    // when / then 02:00Z is still the evening of the 3rd there
    expect(entryDayIso(oneOff)).toBe("2026-09-03");
  });

  it("falls back to the anchor when the entry carries no instant", () => {
    // when / then
    expect(
      entryDayIso(aScheduleEntry({ ...anchored(), scheduledAt: null })),
    ).toBe("2026-09-02");
  });

  it("answers with no day when the entry carries neither", () => {
    // when / then
    expect(
      entryDayIso(aScheduleEntry({ scheduledAt: null, occurrenceDate: null })),
    ).toBe("");
  });
});
