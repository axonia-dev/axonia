import { describe, expect, it } from "vitest";

import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import {
  absorbKeyframes,
  defaultSurvivor,
  resolveMerge,
} from "@/features/expertise/ui/merge-helpers";

const ORIGIN = { x: 0, y: 0, scaleX: 1, scaleY: 1 };

const areas: ExpertiseArea[] = [
  { canonicalTag: "yes", variants: [], peopleCount: 3 },
  { canonicalTag: "ok", variants: [], peopleCount: 1 },
  { canonicalTag: "yep", variants: [], peopleCount: 0 },
];

describe("defaultSurvivor", () => {
  it("returns null when nothing is selected", () => {
    expect(defaultSurvivor([], areas)).toBeNull();
  });

  it("picks the most-used selected tag", () => {
    expect(defaultSurvivor(["ok", "yes", "yep"], areas)).toBe("yes");
  });

  it("treats unknown tags as zero", () => {
    expect(defaultSurvivor(["ghost", "ok"], areas)).toBe("ok");
  });
});

describe("resolveMerge", () => {
  it("is null with no drop target", () => {
    expect(resolveMerge("ok", null)).toBeNull();
  });
  it("is null when dropped on itself", () => {
    expect(resolveMerge("ok", "ok")).toBeNull();
  });
  it("makes the drop target the canonical", () => {
    expect(resolveMerge("ok", "yes")).toEqual({
      variant: "ok",
      canonical: "yes",
    });
  });
});

describe("absorbKeyframes", () => {
  it("flies the overlay into the target's centre while shrinking", () => {
    const frames = absorbKeyframes(
      ORIGIN,
      { left: 0, top: 0, width: 100, height: 50 },
      { left: 200, top: 100, width: 100, height: 50 },
    );
    // Centre-to-centre delta: (250-50, 125-25) = (200, 100).
    expect(frames[0]).toMatchObject({ opacity: 1 });
    expect(frames[1]?.opacity).toBe(0);
    expect(frames[1]?.transform).toContain("translate3d(200px, 100px, 0)");
    expect(frames[1]?.transform).toContain("scaleX(0.32)");
  });

  it("collapses in place when there is no target rect", () => {
    const frames = absorbKeyframes({ ...ORIGIN, x: 10, y: 20 }, null, null);
    expect(frames[1]?.transform).toContain("translate3d(10px, 20px, 0)");
    expect(frames[1]?.transform).toContain("scaleX(0.32)");
  });
});
