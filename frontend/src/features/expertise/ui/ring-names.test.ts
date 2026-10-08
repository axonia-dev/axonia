import { describe, expect, it, vi } from "vitest";

import {
  estimateText,
  type MeasureText,
  ringNameStyle,
} from "@/features/expertise/ui/name-style";
import { ringNameAngle } from "@/features/expertise/ui/ring-name-spots";
import {
  fractionOf,
  placeRingNames,
  type RingName,
  RINGS,
} from "@/features/expertise/ui/ring-names";
import {
  apart,
  type Box,
  boxOf,
  crosses,
  distanceTo,
  labelBoxes,
  otherRingsMet,
  type Point,
  square,
  whatItTouches,
} from "@/features/expertise/ui/ring-names-fixture";
import {
  axisAngle,
  type LabelSpec,
  layoutShape,
  polar,
  viewBoxOf,
} from "@/features/expertise/ui/shape-layout";
import { TIER_META } from "@/features/expertise/ui/tier-meta";

function spokes(count: number, radius: number): [Point, Point][] {
  return Array.from({ length: count }, (_, i) => [
    [0, 0],
    polar(axisAngle(i, count), radius),
  ]);
}

function vertices(count: number, radius: number): Point[] {
  return Array.from({ length: count }, (_, i) =>
    polar(axisAngle(i, count), radius * fractionOf(RINGS[i % 3] ?? null)),
  );
}

interface Scene {
  radius: number;
  dots: readonly Point[];
  outline: readonly Point[];
  boxes: Box[];
}

function expectClear(names: readonly RingName[], scene: Scene) {
  expect(names.map((n) => n.reach)).toEqual(RINGS);
  const boxes = names.map((name) => boxOf(name));
  names.forEach((name) =>
    expect(otherRingsMet(name, scene.radius)).toEqual([]),
  );
  boxes.forEach((box, i) => {
    for (const dot of scene.dots)
      expect(distanceTo(box, dot)).toBeGreaterThan(6);
    scene.outline.forEach((from, j) =>
      expect(
        crosses(box, from, scene.outline.at((j + 1) % scene.outline.length)!),
      ).toBe(false),
    );
    for (const other of [...scene.boxes, ...boxes.slice(i + 1)])
      expect(apart(box, other)).toBe(true);
  });
}

function neighbourhoods(names: readonly string[], reached: number[]) {
  return names.map((name, i): LabelSpec => ({
    text: `AROUND ${name}`,
    kind: "neighbourhood",
    marks: i % 4 === 0 ? 1 : 0,
    reached: reached.includes(i),
  }));
}

const SIXTEEN = neighbourhoods(
  [
    "KAFKA",
    "KUBERNETES",
    "POSTGRES",
    "REACT",
    "JAVA",
    "TERRAFORM",
    "GRAPHQL",
    "SPRING",
    "PYTHON",
    "RUST",
    "DOCKER",
    "REDIS",
    "ANGULAR",
    "GO",
    "SWIFT",
    "KOTLIN",
  ],
  [0, 1, 2, 4, 8, 12],
);

const ALICE = [
  ...neighbourhoods(["POSTGRES", "REACT", "OBSERVABILITY"], [0]),
  ...["Development", "Infrastructure", "Styling"].map((text, i): LabelSpec => ({
    text,
    kind: "named",
    marks: i < 2 ? 1 : 0,
    reached: i === 0,
  })),
];

/** As the drawing sets it: a reached spoke's vertex on the outer ring with its dot, any other at the centre. */
function drawing(specs: readonly LabelSpec[], width: number) {
  const layout = layoutShape(specs, width);
  const outline = specs.map((spec, i) =>
    polar(axisAngle(i, specs.length), spec.reached ? layout.radius : 0),
  );
  const dots = outline.filter((_, i) => specs.at(i)?.reached === true);
  return { layout, dots, outline };
}

describe("ringNameAngle", () => {
  it("points straight down between the two bottom spokes of an odd count", () => {
    expect(ringNameAngle(5)).toBeCloseTo(Math.PI / 2);
  });

  it("turns off the bottom spoke of an even count onto the bisector beside it", () => {
    expect(ringNameAngle(4)).toBeCloseTo(Math.PI / 2 + Math.PI / 4);
    expect(ringNameAngle(16)).toBeCloseTo(Math.PI / 2 + Math.PI / 16);
  });
});

describe("placeRingNames", () => {
  it("sets each name clear of every spoke where the gap has room", () => {
    const names = placeRingNames(3, 90, { dots: [], viewBox: square(90) });
    names.forEach((name) => {
      expect(name.fontSize).toBe(9);
      for (const [from, to] of spokes(3, 90))
        expect(crosses(boxOf(name), from, to)).toBe(false);
    });
  });

  it("lets a name on alice's 293px phone drawing cross another ring rather than touch the outline", () => {
    const { layout, dots, outline } = drawing(ALICE, 293);
    const [interested, ...outer] = placeRingNames(6, layout.radius, {
      dots,
      outline,
      names: layout.labels,
      viewBox: viewBoxOf(layout),
    });
    for (const name of [interested!, ...outer])
      expect(
        whatItTouches(boxOf(name), dots, outline, viewBoxOf(layout)),
      ).toEqual([]);
    expect(otherRingsMet(interested!, layout.radius)).toEqual(["CONTRIBUTOR"]);
    for (const name of outer)
      expect(otherRingsMet(name, layout.radius)).toEqual([]);
  });

  it("crosses a spoke only for a name whose gap has no room clear of the spokes", () => {
    const [interested, ...outer] = placeRingNames(6, 90, {
      dots: [],
      viewBox: square(90),
    });
    const crossesAny = (name: RingName) =>
      spokes(6, 90).some(([from, to]) => crosses(boxOf(name), from, to));
    expect(crossesAny(interested!)).toBe(true);
    expect(outer.some(crossesAny)).toBe(false);
  });

  it("moves a name off a dot on its own ring", () => {
    const dots = [3, 4].map((i) => polar(axisAngle(i, 7), 25));
    const names = placeRingNames(7, 75, { dots, viewBox: square(75) });
    expectClear(names, { radius: 75, dots, outline: [], boxes: [] });
  });

  it.each([3, 5, 6, 7, 9, 12, 16])(
    "names all three rings of %i spokes, clear of every dot, the outline and each other",
    (count) => {
      for (const radius of [68, 75, 90, 120, 150]) {
        const outline = vertices(count, radius);
        const names = placeRingNames(count, radius, {
          dots: outline,
          outline,
          viewBox: square(radius),
        });
        expectClear(names, { radius, dots: outline, outline, boxes: [] });
        for (const name of names) expect(name.fontSize).toBeGreaterThan(7);
      }
    },
  );

  it("steps a name down to 8px where 9px finds no room", () => {
    const outline = vertices(16, 68);
    const names = placeRingNames(16, 68, {
      dots: outline,
      outline,
      viewBox: square(68),
    });
    expect(names.map((n) => n.fontSize)).toContain(8);
    expectClear(names, { radius: 68, dots: outline, outline, boxes: [] });
  });

  it.each([
    ["sixteen spokes", SIXTEEN],
    ["alice's six", ALICE],
  ])(
    "names all three rings of %s in a 311px drawing, clear of the names",
    (_, specs) => {
      const { layout, dots, outline } = drawing(specs, 311);
      const names = placeRingNames(specs.length, layout.radius, {
        dots,
        outline,
        names: layout.labels,
        viewBox: viewBoxOf(layout),
      });
      expectClear(names, {
        radius: layout.radius,
        dots,
        outline,
        boxes: labelBoxes(layout.labels),
      });
    },
  );

  it("measures each name as it is drawn", () => {
    const measure = vi.fn<MeasureText>(estimateText);
    placeRingNames(7, 90, { dots: [], viewBox: square(90) }, measure);
    for (const reach of RINGS)
      expect(measure).toHaveBeenCalledWith(
        TIER_META[reach].label,
        ringNameStyle(9),
      );
  });

  it("measures each name once per size, however many spots it tries", () => {
    const measure = vi.fn<MeasureText>(estimateText);
    const outline = vertices(16, 68);
    const names = placeRingNames(
      16,
      68,
      { dots: outline, outline, viewBox: square(68) },
      measure,
    );
    expect(names.map((n) => n.fontSize)).toContain(8);
    expect(measure.mock.calls.length).toBeLessThanOrEqual(2 * RINGS.length);
  });
});
