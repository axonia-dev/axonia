import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { ZOOM_MAX, ZOOM_MIN } from "@/features/expertise/ui/terrain-aim";
import {
  cameraEye,
  facesCamera,
  facingOf,
  MARK_LIFT,
  projectToBox,
  terrainPointOf,
  terrainSpotOf,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import {
  BOX as size,
  threeSpot,
  viewOf as view,
} from "@/features/expertise/ui/terrain-camera-fixture";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import {
  GLOBE_RADIUS,
  type Surface,
  surfacePoint,
} from "@/features/expertise/ui/terrain-surface";

function mark(signalId: number, x: number, y: number): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x,
    y,
    slot: 0,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: 0,
    reach: null,
  };
}

describe("projectToBox", () => {
  const points = [
    new THREE.Vector3(0, 0, 0),
    new THREE.Vector3(120, 30, -80),
    new THREE.Vector3(-160, 5, 90),
    new THREE.Vector3(40, 150, 60),
  ];

  it.each([ZOOM_MIN, 1, 1.6, ZOOM_MAX])(
    "agrees with the scene's own camera at zoom %s",
    (zoom) => {
      for (const projection of ["globe", "flat"] as const) {
        const v = view(zoom, projection);
        for (const point of points) {
          const mine = projectToBox(point, v);
          const theirs = threeSpot(point, v);

          expect(mine).not.toBeNull();
          expect(mine!.left).toBeCloseTo(theirs.left, 6);
          expect(mine!.top).toBeCloseTo(theirs.top, 6);
        }
      }
    },
  );

  it("puts the centre of the terrain in the middle of the box", () => {
    const spot = projectToBox({ x: 0, y: 0, z: 0 }, view(1, "globe"));

    expect(spot!.left).toBeCloseTo(0.5, 9);
    expect(spot!.top).toBeCloseTo(0.5, 9);
  });

  it("drops a point behind the camera rather than folding it back into the box", () => {
    const v = view(1, "globe");
    const eye = cameraEye(v);

    expect(projectToBox({ x: 0, y: eye.y + 40, z: eye.z + 40 }, v)).toBeNull();
  });

  it("frames the whole flat plane inside the box", () => {
    const v = view(1, "flat");
    for (const x of [-size.width / 2, size.width / 2]) {
      const spot = projectToBox({ x, y: 0, z: 0 }, v)!;

      expect(spot.left).toBeGreaterThan(0);
      expect(spot.left).toBeLessThan(1);
    }
  });
});

describe("facesCamera", () => {
  const v = view(1, "globe");
  const front = { x: 0, y: GLOBE_RADIUS * 0.6, z: GLOBE_RADIUS * 0.8 };

  it("keeps the hemisphere leaning towards the eye", () => {
    expect(facesCamera(front, v)).toBe(true);
  });

  it("hides the hemisphere turned away from it", () => {
    expect(facesCamera({ x: -front.x, y: -front.y, z: -front.z }, v)).toBe(
      false,
    );
  });

  it("reads the tilt rather than the z axis", () => {
    const underside = { x: 0, y: -GLOBE_RADIUS * 0.94, z: GLOBE_RADIUS * 0.34 };

    expect(underside.z).toBeGreaterThan(0);
    expect(facesCamera(underside, v)).toBe(false);
  });

  it("measures how squarely a point faces the eye, not merely whether it does", () => {
    const limb = { x: GLOBE_RADIUS, y: 0, z: 0 };

    expect(facingOf(front, v)).toBeGreaterThan(facingOf(limb, v));
    expect(facingOf(limb, v)).toBeLessThan(0.2);
  });
});

/**
 * The names the drawing carries are placed through this, not through a second copy of the projection. A word out on
 * the limb is drawn along the edge of the globe and half of it lands on the page behind: a three-pixel mark survives
 * that, a word does not.
 */
describe("terrainPointOf", () => {
  const marks = [mark(1, 200, 150)];

  function surfaceOf(projection: TerrainView["projection"]): Surface {
    return { field: buildHeightfield(marks, size), size, projection };
  }

  it("places a point exactly where the mark standing on it goes", () => {
    const surface = surfaceOf("flat");
    const v = view(1, "flat");

    expect(terrainPointOf(surface, v)(200, 150)).toStrictEqual(
      terrainSpotOf(surface, v)(marks[0]!),
    );
  });

  it("refuses a point the globe does not hold squarely enough to carry a word", () => {
    const surface = surfaceOf("globe");
    const v = view(1, "globe");
    const middle = { x: size.width / 2, y: size.height / 2 };
    const offCentre = { x: size.width * 0.4, y: size.height / 2 };

    expect(terrainPointOf(surface, v, 0.7)(middle.x, middle.y)).not.toBeNull();
    expect(terrainPointOf(surface, v)(offCentre.x, offCentre.y)).not.toBeNull();
    expect(
      terrainPointOf(surface, v, 0.7)(offCentre.x, offCentre.y),
    ).toBeNull();
  });

  it("has no far side to hold anything off on the flat reading", () => {
    expect(
      terrainPointOf(surfaceOf("flat"), view(1, "flat"), 0.9)(200, 150),
    ).not.toBeNull();
  });
});

/**
 * The terrain's half of the reading-agnostic overlay: a surface and a camera folded into the one callback the
 * overlay places a button with. It has to agree with the renderer about the mark's lift as well as its position,
 * because the glyph layer is drawn lifted clear of the ground.
 */
describe("terrainSpotOf", () => {
  const marks = [mark(1, 200, 150), mark(2, 600, 380)];

  function surfaceOf(projection: TerrainView["projection"]): Surface {
    return { field: buildHeightfield(marks, size), size, projection };
  }

  it("places a mark where the scene's own camera draws it", () => {
    const surface = surfaceOf("flat");
    const v = view(1, "flat");
    const spotOf = terrainSpotOf(surface, v);

    for (const m of marks) {
      const at = surfacePoint(surface, m.x, m.y, MARK_LIFT);
      const expected = threeSpot(new THREE.Vector3(at.x, at.y, at.z), v);
      const actual = spotOf(m)!;

      expect(actual.left).toBeCloseTo(expected.left, 6);
      expect(actual.top).toBeCloseTo(expected.top, 6);
    }
  });

  it("stands the button on the mark's lift, not on the bare ground", () => {
    const surface = surfaceOf("flat");
    const v = view(1, "flat");
    const at = surfacePoint(surface, marks[0]!.x, marks[0]!.y, 0);

    expect(terrainSpotOf(surface, v)(marks[0]!)!.top).not.toBeCloseTo(
      threeSpot(new THREE.Vector3(at.x, at.y, at.z), v).top,
      6,
    );
  });

  it("has nowhere to put a mark on the hemisphere turned away", () => {
    const spotOf = terrainSpotOf(surfaceOf("globe"), view(1, "globe"));

    // The plane's middle faces the reader and its left edge is the far side, which is the way round that
    // matters: the layout puts every area near the middle.
    expect(spotOf(mark(3, size.width / 2, size.height / 2))).not.toBeNull();
    expect(spotOf(mark(4, 0, size.height / 2))).toBeNull();
  });

  it("has nowhere to put a mark its lift carries out past the limb, though it still faces the reader", () => {
    const surface = surfaceOf("globe");
    const v = view(1, "globe");
    const spotOf = terrainSpotOf(surface, v);
    const row = size.height / 2;
    const across = Array.from(
      { length: size.width * 2 },
      (_, step) => size.width / 2 + step / 4,
    );

    const pastLimb = across.find(
      (x) =>
        facingOf(surfacePoint(surface, x, row, MARK_LIFT), v) > 0 &&
        spotOf(mark(6, x, row)) === null,
    );

    expect(pastLimb).toBeDefined();
    expect(spotOf(mark(7, pastLimb! - 1, row))).not.toBeNull();
  });

  it("drops a mark outside the box rather than pinning it to an edge", () => {
    const spotOf = terrainSpotOf(surfaceOf("flat"), view(1, "flat"));

    expect(spotOf(mark(5, 20_000, 100))).toBeNull();
  });
});
