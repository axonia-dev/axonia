import { describe, expect, it } from "vitest";

import {
  currentMonth,
  formatMonthParam,
  MAX_WINDOW_DAYS,
  MIN_WINDOW_DAYS,
  monthPositionOf,
  nextMonth,
  parseMonthParam,
  parseWindowDays,
  previousMonth,
  sameMonth,
} from "@/features/events/domain/schedule-position";

describe("month arithmetic", () => {
  it("reads the month a moment falls in, in UTC", () => {
    // 23:30 in Los Angeles on Dec 31 is already January 1 in UTC. The suite runs
    // in that zone precisely so a local-time reading shows up here as a failure.
    expect(currentMonth(new Date("2027-01-01T07:30:00Z"))).toEqual({
      year: 2027,
      monthIndex0: 0,
    });
  });

  it("steps back across a year boundary", () => {
    expect(previousMonth({ year: 2026, monthIndex0: 0 })).toEqual({
      year: 2025,
      monthIndex0: 11,
    });
  });

  it("steps forward across a year boundary", () => {
    expect(nextMonth({ year: 2026, monthIndex0: 11 })).toEqual({
      year: 2027,
      monthIndex0: 0,
    });
  });

  it("compares two positions by year and month", () => {
    expect(
      sameMonth({ year: 2026, monthIndex0: 7 }, { year: 2026, monthIndex0: 7 }),
    ).toBe(true);
    expect(
      sameMonth({ year: 2026, monthIndex0: 7 }, { year: 2025, monthIndex0: 7 }),
    ).toBe(false);
  });
});

describe("the month parameter", () => {
  it("writes a padded YYYY-MM", () => {
    expect(formatMonthParam({ year: 2026, monthIndex0: 0 })).toBe("2026-01");
    expect(formatMonthParam({ year: 2026, monthIndex0: 11 })).toBe("2026-12");
  });

  it("reads a padded YYYY-MM back", () => {
    expect(parseMonthParam("2026-08")).toEqual({
      year: 2026,
      monthIndex0: 7,
    });
  });

  it("round-trips every month of a year", () => {
    for (let mi = 0; mi < 12; mi++) {
      const position = { year: 2026, monthIndex0: mi };
      expect(parseMonthParam(formatMonthParam(position))).toEqual(position);
    }
  });

  // A malformed schedule link should land on the schedule, not on an error.
  it.each([
    null,
    "",
    "2026",
    "2026-13",
    "2026-00",
    "2026-8",
    "august",
    "26-08",
  ])("ignores %s rather than throwing", (raw) => {
    expect(parseMonthParam(raw)).toBeNull();
  });
});

describe("the month a date falls in", () => {
  it("reads the year and month straight out of a YYYY-MM-DD date", () => {
    expect(monthPositionOf("2026-08-16")).toEqual({
      year: 2026,
      monthIndex0: 7,
    });
  });

  it("reads the edge months of a year correctly", () => {
    expect(monthPositionOf("2026-01-31")).toEqual({
      year: 2026,
      monthIndex0: 0,
    });
    expect(monthPositionOf("2026-12-01")).toEqual({
      year: 2026,
      monthIndex0: 11,
    });
  });
});

describe("the window parameter", () => {
  it("defaults to the minimum when absent or unreadable", () => {
    expect(parseWindowDays(null)).toBe(MIN_WINDOW_DAYS);
    expect(parseWindowDays("")).toBe(MIN_WINDOW_DAYS);
    expect(parseWindowDays("soon")).toBe(MIN_WINDOW_DAYS);
    expect(parseWindowDays("90.5")).toBe(MIN_WINDOW_DAYS);
  });

  it("keeps a value inside the range", () => {
    expect(parseWindowDays("180")).toBe(180);
  });

  // A hand-edited URL must not be able to ask the API for a decade.
  it("clamps at both ends", () => {
    expect(parseWindowDays("1")).toBe(MIN_WINDOW_DAYS);
    expect(parseWindowDays("-30")).toBe(MIN_WINDOW_DAYS);
    expect(parseWindowDays("99999")).toBe(MAX_WINDOW_DAYS);
  });
});
