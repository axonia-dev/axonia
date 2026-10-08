import * as THREE from "three";
import { describe, expect, it } from "vitest";

import { LOD_ICON_PX } from "@/features/expertise/ui/mark-lod";
import {
  clampPan,
  clampZoom,
  ZOOM_MAX,
  ZOOM_MIN,
} from "@/features/expertise/ui/terrain-aim";
import {
  cameraEye,
  projectToBox,
  tanHalfOf,
  targetOf,
} from "@/features/expertise/ui/terrain-camera";
import {
  BOX as size,
  threeSpot,
  viewOf as view,
} from "@/features/expertise/ui/terrain-camera-fixture";
import { markPxAt } from "@/features/expertise/ui/terrain-mark-size";
import { GLOBE_RADIUS } from "@/features/expertise/ui/terrain-surface";

describe("the zoom range", () => {
  it("delivers the real icon at the near end", () => {
    expect(markPxAt(view(ZOOM_MAX, "flat"))).toBeGreaterThan(LOD_ICON_PX);
    expect(markPxAt(view(ZOOM_MAX, "globe"))).toBeGreaterThan(LOD_ICON_PX);
  });

  it("falls under the icon threshold at the far end, so the shapes are reachable", () => {
    expect(markPxAt(view(ZOOM_MIN, "flat"))).toBeLessThan(LOD_ICON_PX);
  });

  it("holds a wheel past either end", () => {
    expect(clampZoom(40)).toBe(ZOOM_MAX);
    expect(clampZoom(0.01)).toBe(ZOOM_MIN);
    expect(markPxAt(view(40, "flat"))).toBe(markPxAt(view(ZOOM_MAX, "flat")));
  });

  it("narrows the lens as the zoom grows", () => {
    expect(tanHalfOf(view(ZOOM_MAX, "globe"))).toBeLessThan(
      tanHalfOf(view(1, "globe")),
    );
    expect(tanHalfOf(view(1, "globe"))).toBeLessThan(
      tanHalfOf(view(ZOOM_MIN, "globe")),
    );
  });

  /**
   * Zooming by walking the eye in cannot survive either projection: the globe's surface is at
   * {@link GLOBE_RADIUS} and a factor over three puts the camera inside the planet, and a low eye over a wide
   * plane draws its near corner at several times the scale of its far one. The eye holds still and the lens does
   * the work.
   */
  it("leaves the eye where it stands at every zoom", () => {
    for (const projection of ["globe", "flat"] as const) {
      const near = cameraEye(view(ZOOM_MAX, projection));
      const far = cameraEye(view(ZOOM_MIN, projection));

      expect(Math.hypot(near.x, near.y, near.z)).toBeCloseTo(
        Math.hypot(far.x, far.y, far.z),
        9,
      );
    }
  });

  it("keeps the eye outside the globe at every zoom", () => {
    for (const zoom of [ZOOM_MIN, 1, ZOOM_MAX]) {
      const eye = cameraEye(view(zoom, "globe"));

      expect(Math.hypot(eye.x, eye.y, eye.z)).toBeGreaterThan(
        GLOBE_RADIUS * 1.2,
      );
    }
  });

  it("magnifies the terrain as the zoom grows", () => {
    const corner = { x: size.width / 4, y: 0, z: 0 };
    const near = projectToBox(corner, view(ZOOM_MAX, "flat"))!;
    const far = projectToBox(corner, view(1, "flat"))!;

    expect(Math.abs(near.left - 0.5)).toBeGreaterThan(
      Math.abs(far.left - 0.5) * 4,
    );
  });
});

/**
 * Panning is what the flat reading has instead of a far side to turn to: zoomed in, it is the only way to reach a
 * corner. It is bounded by how far the zoom has pushed the picture out of the frame, so the map cannot be dragged
 * off its own box and left as an empty rectangle.
 */
describe("panning", () => {
  it("has nowhere to go at rest, because the whole thing is already in frame", () => {
    expect(clampPan({ x: 400, y: 400 }, size, 1)).toEqual({ x: 0, y: 0 });
  });

  it("reaches further the further in the reader has zoomed", () => {
    const near = clampPan({ x: 1e6, y: 0 }, size, ZOOM_MAX).x;
    const middle = clampPan({ x: 1e6, y: 0 }, size, 2).x;

    expect(middle).toBeGreaterThan(0);
    expect(near).toBeGreaterThan(middle);
    expect(near).toBeLessThan(size.width / 2);
  });

  it("carries the aim across the plane, which carries the picture with it", () => {
    const panned = { ...view(4, "flat"), pan: { x: 120, y: 40 } };

    expect(targetOf(panned)).toEqual({ x: 120, y: 0, z: 40 });
    expect(cameraEye(panned).x).toBeGreaterThan(cameraEye(view(4, "flat")).x);
  });

  /** The globe answers the same need by turning, and an aim carried off its centre would swing it out of frame. */
  it("is ignored on the globe", () => {
    const panned = { ...view(4, "globe"), pan: { x: 200, y: 200 } };

    expect(targetOf(panned)).toEqual({ x: 0, y: 0, z: 0 });
  });

  it("agrees with the scene's own camera once the aim has moved", () => {
    const panned = { ...view(3, "flat"), pan: { x: 90, y: -60 } };
    const point = new THREE.Vector3(140, 12, -70);

    const mine = projectToBox(point, panned)!;
    const theirs = threeSpot(point, panned);

    expect(mine.left).toBeCloseTo(theirs.left, 6);
    expect(mine.top).toBeCloseTo(theirs.top, 6);
  });
});
