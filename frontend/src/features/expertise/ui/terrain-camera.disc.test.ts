import { describe, expect, it } from "vitest";

import {
  globeDiscOf,
  projectToBox,
} from "@/features/expertise/ui/terrain-camera";
import {
  BOX as size,
  viewOf as view,
} from "@/features/expertise/ui/terrain-camera-fixture";
import { GLOBE_RADIUS } from "@/features/expertise/ui/terrain-surface";

/**
 * The outline a reader sees is where sight lines graze the sphere. Sampled on a dense grid of directions, the points
 * that project furthest from the middle of the box are those on the limb, so the widest of them is the outline.
 */
function widestProjected(zoom: number, spin?: { yaw: number; pitch: number }) {
  const v = { ...view(zoom, "globe"), ...(spin ? { spin } : {}) };
  let widest = 0;
  for (let i = 0; i <= 360; i++) {
    for (let j = 1; j < 180; j++) {
      const theta = (i * Math.PI) / 180;
      const phi = (j * Math.PI) / 180;
      const point = {
        x: GLOBE_RADIUS * Math.sin(phi) * Math.cos(theta),
        y: GLOBE_RADIUS * Math.cos(phi),
        z: GLOBE_RADIUS * Math.sin(phi) * Math.sin(theta),
      };
      const spot = projectToBox(point, v);
      if (spot === null) continue;
      widest = Math.max(
        widest,
        Math.hypot(
          (spot.left - 0.5) * size.width,
          (spot.top - 0.5) * size.height,
        ),
      );
    }
  }
  return { v, widest };
}

describe("globeDiscOf", () => {
  it.each([1, 1.6, 3])(
    "is the outline the camera draws, at zoom %s",
    (zoom) => {
      const { v, widest } = widestProjected(zoom);
      const disc = globeDiscOf(v)!;

      expect(disc.x).toBe(size.width / 2);
      expect(disc.y).toBe(size.height / 2);
      expect(disc.r).toBeGreaterThanOrEqual(widest - 0.01);
      expect(disc.r).toBeLessThan(widest + 0.5);
    },
  );

  it("does not move when the globe turns: the camera stays aimed at its centre", () => {
    const { v, widest } = widestProjected(1, { yaw: 70, pitch: 30 });

    expect(globeDiscOf(v)!.r).toBeCloseTo(globeDiscOf(view(1, "globe"))!.r, 6);
    expect(globeDiscOf(v)!.r).toBeLessThan(widest + 0.5);
  });

  it("has no outline on the flat reading", () => {
    expect(globeDiscOf(view(1, "flat"))).toBeNull();
  });
});
