import { describe, expect, it } from "vitest";

import { monotonePath } from "@/features/health/ui/monotone-path";

function numbers(path: string): number[][] {
  return [...path.matchAll(/C([\d.,\s-]+)/g)].map((match) =>
    match[1]!
      .trim()
      .split(/[\s,]+/)
      .map(Number),
  );
}

describe("monotonePath", () => {
  it("passes through every point", () => {
    // when
    const actual = monotonePath([
      { x: 0, y: 170 },
      { x: 100, y: 40 },
      { x: 200, y: 170 },
    ]);

    // then
    expect(actual.startsWith("M0.0,170.0")).toBe(true);
    expect(actual).toContain(" 100.0,40.0");
    expect(actual.endsWith(" 200.0,170.0")).toBe(true);
  });

  it("never draws below a zero bucket or above a peak", () => {
    // when
    const actual = monotonePath([
      { x: 0, y: 170 },
      { x: 100, y: 20 },
      { x: 200, y: 170 },
      { x: 300, y: 170 },
      { x: 400, y: 90 },
    ]);

    // then
    for (const [, c1, , c2] of numbers(actual)) {
      expect(c1).toBeGreaterThanOrEqual(20);
      expect(c1).toBeLessThanOrEqual(170);
      expect(c2).toBeGreaterThanOrEqual(20);
      expect(c2).toBeLessThanOrEqual(170);
    }
  });

  it("keeps a flat run flat", () => {
    // when
    const actual = monotonePath([
      { x: 0, y: 170 },
      { x: 100, y: 170 },
      { x: 200, y: 170 },
    ]);

    // then
    expect(actual).toBe(
      "M0.0,170.0 C33.3,170.0 66.7,170.0 100.0,170.0 C133.3,170.0 166.7,170.0 200.0,170.0",
    );
  });

  it("draws nothing for no points", () => {
    expect(monotonePath([])).toBe("");
  });
});
