import { describe, expect, it } from "vitest";

import { trendText } from "@/features/health/domain/trend";

describe("trendText", () => {
  it("reads a rise against the previous window", () => {
    // when
    const actual = trendText(9, { previous: 6, direction: "UP" });

    // then
    expect(actual).toBe("up from 6");
  });

  it("reads a fall against the previous window", () => {
    // when
    const actual = trendText(1, { previous: 4, direction: "DOWN" });

    // then
    expect(actual).toBe("down from 4");
  });

  it("reads no change as the same as before", () => {
    // when
    const actual = trendText(3, { previous: 3, direction: "FLAT" });

    // then
    expect(actual).toBe("same as before");
  });

  it("says nothing when both windows are empty, so from 0 never reads as noise", () => {
    // when
    const actual = trendText(0, { previous: 0, direction: "FLAT" });

    // then
    expect(actual).toBeNull();
  });

  it("still reads a rise from nothing", () => {
    // when
    const actual = trendText(2, { previous: 0, direction: "UP" });

    // then
    expect(actual).toBe("up from 0");
  });

  it("still reads a fall to nothing", () => {
    // when
    const actual = trendText(0, { previous: 3, direction: "DOWN" });

    // then
    expect(actual).toBe("down from 3");
  });
});
