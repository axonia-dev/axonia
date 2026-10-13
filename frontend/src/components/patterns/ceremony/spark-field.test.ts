import { describe, expect, it } from "vitest";

import {
  createSeeds,
  fitTargets,
  type Point,
  sampleTargets,
  sparkBudget,
  sparksAt,
  spreadTargets,
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

  it("flies in the community's hue", () => {
    // when
    const actual = sparksAt(condense, createSeeds(3, 7), 0.5, field);

    // then
    expect(actual.map((spark) => spark.white)).toEqual([false, false, false]);
  });

  it("burns white and steady once landed", () => {
    // when
    const actual = sparksAt(condense, createSeeds(3, 7), 2, field);

    // then
    expect(actual.map((spark) => [spark.white, spark.alpha])).toEqual([
      [true, 0.9],
      [true, 0.9],
      [true, 0.9],
    ]);
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
    expect(actual).toBe(1100);
  });
});

describe("createSeeds", () => {
  it("scales the sparks' size", () => {
    // when
    const actual = createSeeds(4, 7, 0.5).map((seed) => seed.size);

    // then
    const full = createSeeds(4, 7, 1).map((seed) => seed.size / 2);
    actual.forEach((size, index) => {
      expect(size).toBeCloseTo(full.at(index) ?? 0);
    });
  });
});

function grid(columns: number, rows: number): Point[] {
  return Array.from({ length: columns * rows }, (_, index) => ({
    x: index % columns,
    y: Math.floor(index / columns),
  }));
}

describe("spreadTargets", () => {
  it("keeps every row of the text when it keeps only some of the points", () => {
    // when
    const actual = spreadTargets(grid(40, 10), 100, 7);

    // then
    expect(new Set(actual.map((point) => point.y)).size).toBe(10);
  });

  it("never invents a point", () => {
    // when
    const actual = spreadTargets(grid(3, 1), 10, 7);

    // then
    expect(actual).toHaveLength(3);
  });
});

describe("fitTargets", () => {
  it("keeps the fine step when the points fit the budget", () => {
    // given
    const sample = (step: number) => grid(Math.round(20 / step), 1);

    // when
    const actual = fitTargets(sample, 1, 50);

    // then
    expect(actual.step).toBe(1);
    expect(actual.targets).toHaveLength(20);
  });

  it("samples a coarser grid sized to the budget when there are too many points", () => {
    // given
    const sample = (step: number) =>
      grid(Math.round(40 / step), Math.round(10 / step));

    // when
    const actual = fitTargets(sample, 1, 100);

    // then
    expect(actual.step).toBeCloseTo(2);
    expect(actual.targets).toHaveLength(100);
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
