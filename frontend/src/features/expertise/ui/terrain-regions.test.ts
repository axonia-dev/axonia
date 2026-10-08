import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  nearestRegion,
  type RegionSeed,
  regionSeeds,
} from "@/features/expertise/ui/terrain-shading";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

function topic(tag: string, x: number, y: number): PlacedTopic {
  return { tag, x, y, r: 10, declarations: 0, practised: true };
}

function mark(tag: string, slot: number): PlacedContribution {
  return {
    signalId: 1,
    tag,
    x: 0,
    y: 0,
    slot,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: 0,
    reach: null,
  };
}

describe("regionSeeds", () => {
  it("takes each area's colour from the contributions that landed in it", () => {
    const seeds = regionSeeds(
      [topic("kafka", 0, 0), topic("react", 100, 0)],
      [mark("kafka", 2), mark("react", 5)],
    );

    expect(seeds.map((seed) => seed.slot)).toStrictEqual([2, 5]);
  });

  // An area carried only by declarations has no marks, so it has no elevation either: its region is open water,
  // and colouring it would claim ground nobody has been on.
  it("leaves out an area nothing has landed in", () => {
    const seeds = regionSeeds(
      [topic("kafka", 0, 0), topic("offered-only", 100, 0)],
      [mark("kafka", 0)],
    );

    expect(seeds.map((seed) => seed.x)).toStrictEqual([0]);
  });

  it("reaches as far as the nearest other area, so regions tile at the layout's own scale", () => {
    const seeds = regionSeeds(
      [topic("kafka", 0, 0), topic("react", 60, 0), topic("flink", 200, 0)],
      [mark("kafka", 0), mark("react", 1), mark("flink", 2)],
    );

    expect(seeds.map((seed) => seed.reach)).toStrictEqual([60, 60, 140]);
  });

  it("gives a lone area a reach of its own, having no neighbour to measure against", () => {
    const [only] = regionSeeds([topic("kafka", 0, 0)], [mark("kafka", 0)]);

    expect(only!.reach).toBeGreaterThan(0);
    expect(Number.isFinite(only!.reach)).toBe(true);
  });
});

describe("nearestRegion", () => {
  const seeds: RegionSeed[] = [
    { tag: "kafka", x: 0, y: 0, slot: 1, reach: 100 },
    { tag: "rust", x: 300, y: 0, slot: 2, reach: 100 },
  ];

  it("hands a point to the area whose centre it sits nearest", () => {
    expect(nearestRegion(seeds, 280, 0)?.slot).toBe(2);
  });

  it("is strongest at the centre and gone at the edge of the reach", () => {
    expect(nearestRegion(seeds, 0, 0)?.nearness).toBeCloseTo(1, 6);
    expect(nearestRegion(seeds, 50, 0)?.nearness).toBeCloseTo(0.5, 6);
    expect(nearestRegion(seeds, 100, 0)).toBeNull();
  });

  it("claims nothing where no area reaches", () => {
    expect(nearestRegion(seeds, 150, 400)).toBeNull();
    expect(nearestRegion([], 0, 0)).toBeNull();
  });
});
