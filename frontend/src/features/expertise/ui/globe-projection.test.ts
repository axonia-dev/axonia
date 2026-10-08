import { describe, expect, it } from "vitest";

import {
  planeToSphere,
  type Projection,
} from "@/features/expertise/ui/globe-projection";

const size = { width: 400, height: 300 };
const R = 100;

function lengthOf(v: { x: number; y: number; z: number }): number {
  return Math.hypot(v.x, v.y, v.z);
}

describe("planeToSphere", () => {
  it("puts every point on the sphere", () => {
    for (const [x, y] of [
      [0, 0],
      [200, 150],
      [400, 300],
      [399, 1],
    ] as const) {
      expect(lengthOf(planeToSphere(x, y, size, R))).toBeCloseTo(R, 6);
    }
  });

  it("wraps: the left and right edges meet", () => {
    const left = planeToSphere(0, 150, size, R);
    const right = planeToSphere(400, 150, size, R);

    expect(right.x).toBeCloseTo(left.x, 6);
    expect(right.y).toBeCloseTo(left.y, 6);
    expect(right.z).toBeCloseTo(left.z, 6);
  });

  it("puts the middle of the plane on the equator", () => {
    expect(planeToSphere(200, 150, size, R).y).toBeCloseTo(0, 6);
  });

  it("puts a higher point on the plane further north", () => {
    expect(planeToSphere(200, 50, size, R).y).toBeGreaterThan(
      planeToSphere(200, 250, size, R).y,
    );
  });

  it("keeps latitude off the poles, so nothing is crushed to a point", () => {
    const north = planeToSphere(200, 0, size, R);

    expect(Math.abs(north.y)).toBeLessThan(R * 0.96);
  });

  it("is a pure function of its inputs", () => {
    expect(planeToSphere(123, 45, size, R)).toEqual(
      planeToSphere(123, 45, size, R),
    );
  });
});

describe("Projection", () => {
  it("is exactly the globe and the flat reading of one layout", () => {
    const everyProjection: Record<Projection, true> = {
      globe: true,
      flat: true,
    };

    expect(Object.keys(everyProjection)).toEqual(["globe", "flat"]);
  });

  it("turns the middle of the plane towards the reader", () => {
    // The force layout keeps every area near the middle of the plane. Wrapped from the left edge instead, that
    // middle lands on the far side of the globe and the reader is shown the one hemisphere with nothing on it.
    const middle = planeToSphere(size.width / 2, size.height / 2, size, 100);
    const edge = planeToSphere(0, size.height / 2, size, 100);

    expect(middle.z).toBeCloseTo(100, 6);
    expect(edge.z).toBeCloseTo(-100, 6);
  });
});
