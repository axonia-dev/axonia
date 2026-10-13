import { describe, expect, it } from "vitest";

import {
  createSeeds,
  sampleTargets,
  sparkBudget,
  sparksAt,
} from "./spark-field";

const field = { width: 1280, height: 800 };
const targets = [
  { x: 100, y: 200 },
  { x: 140, y: 210 },
  { x: 180, y: 220 },
];
const condense = {
  kind: "condense" as const,
  targets,
  beat: { start: 0.15, duration: 1.8 },
  fadeOut: { start: 2.25, duration: 0.6 },
};

describe("createSeeds", () => {
  it("is deterministic for a seed", () => {
    // when
    const actual = [createSeeds(5, 7), createSeeds(5, 7)];

    // then
    expect(actual[0]).toEqual(actual[1]);
  });
});

describe("sparksAt condense", () => {
  it("starts every spark outside the field", () => {
    // given
    const seeds = createSeeds(3, 7);

    // when
    const actual = sparksAt(condense, seeds, 0, field);

    // then
    for (const spark of actual) {
      const fromCentre = Math.hypot(spark.x - 640, spark.y - 400);
      expect(fromCentre).toBeGreaterThan(640);
    }
  });

  it("lands every spark exactly on its target", () => {
    // given
    const seeds = createSeeds(3, 7);

    // when
    const actual = sparksAt(condense, seeds, 2, field);

    // then
    actual.forEach((spark, index) => {
      expect(spark.x).toBeCloseTo(targets.at(index)?.x ?? 0, 6);
      expect(spark.y).toBeCloseTo(targets.at(index)?.y ?? 0, 6);
    });
  });

  it("is gone once faded out", () => {
    // when
    const actual = sparksAt(condense, createSeeds(3, 7), 3, field);

    // then
    expect(actual).toEqual([]);
  });

  it("uses no more sparks than there are targets", () => {
    // when
    const actual = sparksAt(condense, createSeeds(50, 7), 1, field);

    // then
    expect(actual).toHaveLength(3);
  });
});

describe("sparksAt stream and gather", () => {
  it("streams nothing before its beat", () => {
    // when
    const actual = sparksAt(
      {
        kind: "stream",
        origin: { x: 640, y: 400 },
        height: 400,
        beat: { start: 0.3, duration: 1.7 },
      },
      createSeeds(20, 3),
      0,
      field,
    );

    // then
    expect(actual).toEqual([]);
  });

  it("gathers nothing once gathered", () => {
    // when
    const actual = sparksAt(
      {
        kind: "gather",
        target: { x: 640, y: 300 },
        beat: { start: 1.55, duration: 0.95 },
      },
      createSeeds(20, 3),
      2.6,
      field,
    );

    // then
    expect(actual).toEqual([]);
  });
});

describe("sparkBudget", () => {
  it("spends fewer sparks on a phone than on a desktop", () => {
    // when
    const actual = [sparkBudget(412, 892, 1), sparkBudget(1280, 800, 1)];

    // then
    expect(actual[0]).toBeLessThan(actual[1] ?? 0);
  });

  it("spends fewer sparks on a dense screen", () => {
    // when
    const actual = [sparkBudget(1280, 800, 3), sparkBudget(1280, 800, 1)];

    // then
    expect(actual[0]).toBeLessThan(actual[1] ?? 0);
  });

  it("never drops below a floor", () => {
    // when
    const actual = sparkBudget(100, 100, 1);

    // then
    expect(actual).toBe(120);
  });
});

describe("sampleTargets", () => {
  it("keeps the opaque pixels on the step grid", () => {
    // given a 4x2 image whose only opaque pixel is (2, 0)
    const data = new Uint8ClampedArray(4 * 2 * 4);
    data[2 * 4 + 3] = 255;

    // when
    const actual = sampleTargets({ width: 4, height: 2, data }, 1);

    // then
    expect(actual).toEqual([{ x: 2, y: 0 }]);
  });
});
