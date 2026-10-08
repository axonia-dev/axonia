import { describe, expect, it } from "vitest";

import {
  nearestMark,
  reachFor,
} from "@/features/expertise/ui/contribution-picking";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Spot } from "@/features/expertise/ui/terrain-projection";

const size = { width: 800, height: 500 };
const reach = reachFor(10);

function mark(signalId: number): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x: 0,
    y: 0,
    slot: 0,
    sourceType: "SLOT",
    userId: "u-1",
    occurredAt: 0,
    reach: null,
  };
}

/** The projection the drawing was painted with, stated here in pixels so the arithmetic is readable. */
function at(spots: Record<number, [number, number] | null>) {
  return (m: PlacedContribution): Spot | null => {
    const spot = spots[m.signalId];
    return spot === undefined || spot === null
      ? null
      : { left: spot[0] / size.width, top: spot[1] / size.height };
  };
}

function aimedAt(
  spots: Record<number, [number, number] | null>,
  x: number,
  y: number,
) {
  return nearestMark({
    marks: Object.keys(spots).map((id) => mark(Number(id))),
    spotOf: at(spots),
    size,
    at: { x, y },
    reach,
  });
}

describe("reachFor", () => {
  it("grows the target with the mark", () => {
    expect(reachFor(30)).toBeGreaterThan(reachFor(20));
  });

  /** A five-pixel mark is a five-pixel mark, and a finger is not. */
  it("keeps a small mark hittable rather than letting the target shrink with it", () => {
    expect(reachFor(1)).toBeGreaterThanOrEqual(14);
  });
});

describe("nearestMark", () => {
  it("finds the mark the pointer is on", () => {
    expect(aimedAt({ 1: [100, 100] }, 102, 98)?.signalId).toBe(1);
  });

  it("finds nothing over open ground", () => {
    expect(aimedAt({ 1: [100, 100] }, 400, 400)).toBeNull();
  });

  /**
   * The focusable overlay can only give a mark a button where a whole undisturbed target fits, which leaves a reader
   * zoomed in on two marks a few pixels apart able to reach neither. Here they share the ground between them.
   */
  it("picks the nearer of two marks close enough to disturb each other", () => {
    const spots = { 1: [100, 100], 2: [106, 100] } satisfies Record<
      number,
      [number, number]
    >;

    expect(aimedAt(spots, 101, 100)?.signalId).toBe(1);
    expect(aimedAt(spots, 105, 100)?.signalId).toBe(2);
  });

  it("ignores a mark the camera is not pointed at", () => {
    expect(aimedAt({ 1: null, 2: [300, 300] }, 300, 300)?.signalId).toBe(2);
  });

  it("counts a mark exactly at the edge of the reach as aimed at", () => {
    expect(aimedAt({ 1: [100, 100] }, 100 + reach, 100)?.signalId).toBe(1);
  });
});
