import { describe, expect, it } from "vitest";

import { inside, shiftInto } from "@/features/expertise/ui/text-box";

const BOUNDS = { left: -10, right: 10, top: -5, bottom: 5 };

describe("inside", () => {
  it("holds a box that touches the bounds' edges", () => {
    expect(inside(BOUNDS, BOUNDS)).toBe(true);
  });

  it.each([
    ["left", { ...BOUNDS, left: -11 }],
    ["right", { ...BOUNDS, right: 11 }],
    ["top", { ...BOUNDS, top: -6 }],
    ["bottom", { ...BOUNDS, bottom: 6 }],
  ])("rejects a box past the %s edge", (_, box) => {
    expect(inside(box, BOUNDS)).toBe(false);
  });
});

describe("shiftInto", () => {
  it("leaves a span already within alone", () => {
    expect(shiftInto([-2, 3], [-10, 10])).toBe(0);
  });

  it("moves a span back from either end", () => {
    expect(shiftInto([-12, -2], [-10, 10])).toBe(2);
    expect(shiftInto([4, 13], [-10, 10])).toBe(-3);
  });

  it("starts a span wider than the bounds at their low end", () => {
    expect(shiftInto([-15, 15], [-10, 10])).toBe(5);
  });
});
