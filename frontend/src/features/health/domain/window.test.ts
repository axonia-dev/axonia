import { describe, expect, it } from "vitest";

import {
  bucketSpans,
  dayMonth,
  dayRange,
  fractionOf,
  wholeDaysBetween,
  windowDaysOf,
  windowSpan,
} from "@/features/health/domain/window";

describe("windowDaysOf", () => {
  it("reads the days off the window", () => {
    expect(windowDaysOf("30d")).toBe(30);
  });

  it("falls back to ninety days for a window it cannot read", () => {
    expect(windowDaysOf("quarter")).toBe(90);
  });
});

describe("bucketSpans", () => {
  it("splits the window into equal buckets that tile it exactly", () => {
    // given
    const span = windowSpan("90d", "2026-11-09T12:00:00Z");

    // when
    const actual = bucketSpans(span, 12);

    // then
    expect(actual).toHaveLength(12);
    expect(actual[0]?.startMs).toBe(span.startMs);
    expect(actual[11]?.endMs).toBe(span.endMs);
    expect(actual[3]?.startMs).toBe(span.startMs + 3 * 7.5 * 86_400_000);
  });
});

describe("fractionOf", () => {
  it("places an instant across the window", () => {
    // given
    const span = windowSpan("30d", "2026-11-09T12:00:00Z");

    // when
    const actual = fractionOf(span, "2026-10-25T12:00:00Z");

    // then
    expect(actual).toBe(0.5);
  });
});

describe("dayMonth", () => {
  it("names the day in the reader's zone, so a 19:00 New York session on 4 Dec reads 4 Dec", () => {
    expect(dayMonth(Date.parse("2026-12-05T00:00:00Z"))).toBe("4 Dec");
  });
});

describe("wholeDaysBetween", () => {
  it("counts whole days, rounding a part day down", () => {
    expect(
      wholeDaysBetween("2026-09-09T10:00:00Z", "2026-11-09T12:00:00Z"),
    ).toBe(61);
  });
});

describe("dayRange", () => {
  it("names every day a bucket touches, so an instant at an edge is never dated to a neighbour", () => {
    // given
    const [first, second] = bucketSpans(
      windowSpan("90d", "2026-11-09T12:00:00Z"),
      12,
    );

    // when
    const actual = [dayRange(first!), dayRange(second!)];

    // then
    expect(actual).toEqual(["11 Aug to 18 Aug", "18 Aug to 26 Aug"]);
  });

  it("names a bucket inside one day once", () => {
    // 01:00 to 12:00 on the test zone's 9 Nov
    expect(
      dayRange({
        startMs: Date.parse("2026-11-09T09:00:00Z"),
        endMs: Date.parse("2026-11-09T20:00:00Z"),
      }),
    ).toBe("9 Nov");
  });
});
