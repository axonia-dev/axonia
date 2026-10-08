import { describe, expect, it } from "vitest";

import {
  absoluteTime,
  relativeTime,
  startOfMonthIso,
  startOfQuarterIso,
} from "@/lib/period";

// Use a UTC-noon reference so local-timezone offsets cannot shift the date.
const now = new Date("2026-06-12T12:00:00Z");

describe("startOfMonthIso", () => {
  it("returns the first instant of the current local month", () => {
    // when
    const actual = startOfMonthIso(now);

    // then
    expect(actual).toBe(new Date(2026, 5, 1).toISOString());
  });
});

describe("startOfQuarterIso", () => {
  it("returns the first instant of the current local quarter", () => {
    // when
    const actual = startOfQuarterIso(now);

    // then — June sits in Q2, which starts in April
    expect(actual).toBe(new Date(2026, 3, 1).toISOString());
  });
});

describe("relativeTime", () => {
  it("renders past instants", () => {
    // when
    const actual = relativeTime("2026-06-09T12:00:00Z", now);

    // then
    expect(actual).toBe("3 days ago");
  });

  it("renders future instants", () => {
    // when
    const actual = relativeTime("2026-06-19T12:00:00Z", now);

    // then
    expect(actual).toBe("in 7 days");
  });

  it("renders sub-minute deltas as just now", () => {
    // when
    const actual = relativeTime("2026-06-12T12:00:30Z", now);

    // then
    expect(actual).toBe("just now");
  });
});

// The suite pins TZ to America/Los_Angeles, so a UTC-noon instant renders on the same
// calendar day at 5 in the morning: the assertion states the local reading, not the ISO one.
describe("absoluteTime", () => {
  it("spells the exact moment out in the viewer's own zone", () => {
    // when
    const actual = absoluteTime("2026-06-12T12:00:00Z");

    // then
    expect(actual).toBe("Jun 12, 2026, 5:00 AM");
  });
});
