import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import {
  GLOBE_RADIUS,
  type Surface,
  surfacePoint,
} from "@/features/expertise/ui/terrain-surface";

const size = { width: 800, height: 500 };

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

const crowd = Array.from({ length: 12 }, (_, index) =>
  mark(index, 400 + (index % 3), 250 + (index % 4)),
);

function surfaceOf(
  marks: PlacedContribution[],
  projection: Surface["projection"],
): Surface {
  return { field: buildHeightfield(marks, size), size, projection };
}

describe("surfacePoint", () => {
  it("centres the flat plane on the origin", () => {
    const point = surfacePoint(surfaceOf([], "flat"), 400, 250, 0);

    expect(point).toEqual({ x: 0, y: 0, z: 0 });
  });

  /**
   * Height is carried by colour, not by geometry. Displaced, the saturating heightfield made land a plateau with a
   * cliff at the coast, and a cliff a tenth of the radius high turned the globe into a stack of mesas.
   */
  it("leaves the ground level however much has piled up on it", () => {
    const empty = surfacePoint(surfaceOf([], "flat"), 400, 250, 0);
    const dense = surfacePoint(surfaceOf(crowd, "flat"), 400, 250, 0);

    expect(dense.y).toBe(empty.y);
  });

  it("lifts a mark clear of the ground it stands on", () => {
    const ground = surfacePoint(surfaceOf(crowd, "flat"), 400, 250, 0);
    const lifted = surfacePoint(surfaceOf(crowd, "flat"), 400, 250, 4);

    expect(lifted.y - ground.y).toBeCloseTo(4, 6);
  });

  it("carries a lift onto the globe as radius, so the sphere stays a sphere", () => {
    const globe = surfaceOf(crowd, "globe");
    const on = surfacePoint(globe, 400, 250, 0);
    const above = surfacePoint(globe, 400, 250, 9);

    expect(Math.hypot(on.x, on.y, on.z)).toBeCloseTo(GLOBE_RADIUS, 6);
    expect(Math.hypot(above.x, above.y, above.z)).toBeCloseTo(
      GLOBE_RADIUS + 9,
      6,
    );
  });
});
