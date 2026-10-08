import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { groupByDate } from "@/features/events/domain/month-grid";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { selectedKeyFor } from "@/features/events/ui/session-selection";

function entry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: 7,
    seriesId: 1,
    title: "Guild Sync",
    occurrenceDate: "2026-08-20",
    scheduledAt: "2026-08-20T12:00:00Z",
    hasTime: true,
    seriesTimeZone: "Europe/Zurich",
    durationMinutes: 60,
    status: "SCHEDULED",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

describe("selectedKeyFor", () => {
  it("keys the chip the grid holds the selected entry under", () => {
    const selected = entry();
    expect(selectedKeyFor(selected, groupByDate([selected]))).toBe("7");
  });

  it("keys nothing while nothing is selected", () => {
    expect(selectedKeyFor(null, new Map())).toBeUndefined();
  });

  /**
   * A reschedule, a skip or a cancel invalidates the schedule, and the refreshed answer keeps
   * object identity for every entry that did not change while minting a new one for the entry that
   * did. The selection is held as that object, so it stops being found on its own day: the panel
   * would otherwise go on stating the hour the entry had before the move.
   */
  it("keys nothing once the schedule holds a new object for the entry", () => {
    const selected = entry();
    const moved = { ...selected, scheduledAt: "2026-08-20T15:00:00Z" };
    expect(selectedKeyFor(selected, groupByDate([moved]))).toBeUndefined();
  });

  it("keys nothing once the entry's day holds no entries at all", () => {
    expect(selectedKeyFor(entry(), groupByDate([]))).toBeUndefined();
  });
});
