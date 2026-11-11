import { describe, expect, it } from "vitest";

import { declaredMonth, declaredOn, monthName } from "@/lib/declared-on";

const NOW = new Date("2026-11-11T10:00:00Z");

describe("declaredOn", () => {
  it("formats the declaration date as a day with a three-letter month", () => {
    expect(declaredOn("2026-09-12T12:00:00Z")).toBe("12 Sep 2026");
    expect(declaredOn("2026-06-01T12:00:00Z")).toBe("1 Jun 2026");
  });
});

describe("declaredMonth", () => {
  it("formats the declaration date as a three-letter month and a year", () => {
    expect(declaredMonth("2026-09-12T12:00:00Z")).toBe("Sep 2026");
    expect(declaredMonth("2026-03-02T09:00:00Z")).toBe("Mar 2026");
  });

  it("reads the month in UTC, like monthName", () => {
    // when
    const actual = declaredMonth("2026-10-01T00:30:00Z");

    // then
    expect(actual).toBe("Oct 2026");
  });
});

describe("monthName", () => {
  it("names the three-letter month of the instant in UTC", () => {
    // when
    const actual = monthName("2026-09-30T23:30:00Z", NOW);

    // then
    expect(actual).toBe("Sep");
  });

  it("adds the year when it is not the current one", () => {
    // when
    const actual = monthName("2025-11-30T23:30:00Z", NOW);

    // then
    expect(actual).toBe("Nov 2025");
  });

  it("judges the current year in UTC", () => {
    // when
    const actual = monthName("2026-01-01T00:30:00Z", NOW);

    // then
    expect(actual).toBe("Jan");
  });
});
