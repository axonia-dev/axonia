import { describe, expect, it } from "vitest";

import {
  easeInOutCubic,
  easeOutBack,
  easeOutCubic,
  lerp,
  progress,
} from "./timeline";

const beat = { start: 1, duration: 2 };

describe("progress", () => {
  it("is 0 before the beat starts", () => {
    // when
    const actual = progress(beat, 0.5);

    // then
    expect(actual).toBe(0);
  });

  it("is proportional inside the beat", () => {
    // when
    const actual = progress(beat, 2);

    // then
    expect(actual).toBe(0.5);
  });

  it("is 1 once the beat is over", () => {
    // when
    const actual = progress(beat, 9);

    // then
    expect(actual).toBe(1);
  });

  it("jumps straight to 1 for an instant beat", () => {
    // when
    const actual = progress({ start: 3, duration: 0 }, 3);

    // then
    expect(actual).toBe(1);
  });
});

describe("easing", () => {
  it.each([easeInOutCubic, easeOutCubic, easeOutBack])(
    "starts at 0 and ends at 1",
    (ease) => {
      // when
      const actual = [ease(0), ease(1)];

      // then
      expect(actual[0]).toBeCloseTo(0);
      expect(actual[1]).toBeCloseTo(1);
    },
  );

  it("overshoots on the way in with easeOutBack", () => {
    // when
    const actual = easeOutBack(0.7);

    // then
    expect(actual).toBeGreaterThan(1);
  });
});

describe("lerp", () => {
  it("interpolates between two values", () => {
    // when
    const actual = lerp(10, 20, 0.25);

    // then
    expect(actual).toBe(12.5);
  });
});
