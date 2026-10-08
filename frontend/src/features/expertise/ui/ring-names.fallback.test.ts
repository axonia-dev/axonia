import { describe, expect, it } from "vitest";

import type { ShapeReach } from "@/features/expertise/domain/practitioner-shape";
import {
  estimateText,
  type MeasureText,
} from "@/features/expertise/ui/name-style";
import {
  fractionOf,
  placeRingNames,
  RINGS,
} from "@/features/expertise/ui/ring-names";
import {
  apart,
  boxOf,
  type Point,
  square,
  whatItTouches,
  within,
} from "@/features/expertise/ui/ring-names-fixture";
import {
  axisAngle,
  type LabelSpec,
  layoutShape,
  type PlacedLabel,
  polar,
  viewBoxOf,
} from "@/features/expertise/ui/shape-layout";
import { TIER_META } from "@/features/expertise/ui/tier-meta";

type Reach = ShapeReach | null;

const WORDS = [
  "AROUND POSTGRES",
  "Infrastructure",
  "AROUND OBSERVABILITY",
  "Development",
  "Styling",
  "AROUND KAFKA",
  "Machine learning",
  "Security",
  "AROUND REACT",
  "Data",
  "AROUND KUBERNETES",
  "Testing",
];

const PATTERNS: Record<string, readonly Reach[]> = {
  "all interested": ["INTERESTED"],
  "all contributor": ["CONTRIBUTOR"],
  "every other interested": ["INTERESTED", null],
  "every other contributor": ["CONTRIBUTOR", null],
  mixed: ["CONTRIBUTOR", "INTERESTED", null, "PRACTITIONER", "INTERESTED"],
};

/** As the drawing sets it: each vertex on its reach's ring with a dot, an unreached one at the centre. */
function drawing(reaches: readonly Reach[], width: number) {
  const specs = reaches.map((reach, i): LabelSpec => ({
    text: WORDS[(i * 5 + reaches.length) % WORDS.length]!,
    kind: i % 2 === 0 ? "neighbourhood" : "named",
    marks: i % 3 === 0 ? 1 : 0,
    reached: reach !== null,
  }));
  const layout = layoutShape(specs, width);
  const outline = reaches.map((reach, i) =>
    polar(axisAngle(i, reaches.length), layout.radius * fractionOf(reach)),
  );
  const dots = outline.filter((_, i) => reaches.at(i) !== null);
  return { layout, outline, dots };
}

function reachesOf(pattern: readonly Reach[], count: number): Reach[] {
  return Array.from({ length: count }, (_, i) => pattern[i % pattern.length]!);
}

function block(left: number, top: number, right: number, bottom: number) {
  return {
    lines: ["cover"],
    anchor: "middle",
    textX: 0,
    marksX: 0,
    marksLine: 1,
    top,
    left,
    width: right - left,
    height: bottom - top,
    visible: true,
  } satisfies PlacedLabel;
}

function cover(size: number): PlacedLabel {
  return block(-size, -size, size, size);
}

describe("placeRingNames on a crowded phone drawing", () => {
  it.each([293, 311, 375])(
    "keeps every ring name at %ipx off the vertex dots, the outline and the viewBox's edge",
    (width) => {
      const touching: string[] = [];
      for (const [pattern, reaches] of Object.entries(PATTERNS))
        for (const count of [4, 5, 6, 8, 10, 12, 16]) {
          const { layout, outline, dots } = drawing(
            reachesOf(reaches, count),
            width,
          );
          const names = placeRingNames(count, layout.radius, {
            dots,
            outline,
            names: layout.labels,
            viewBox: viewBoxOf(layout),
          });
          expect(names.map((n) => n.reach)).toEqual(RINGS);
          for (const name of names) {
            const hits = whatItTouches(
              boxOf(name),
              dots,
              outline,
              viewBoxOf(layout),
            );
            if (hits.length > 0)
              touching.push(
                `${pattern} ×${count} ${name.reach}: ${hits.join(",")}`,
              );
          }
        }
      expect(touching).toEqual([]);
    },
  );

  it("sets a name over another name only where no spot is free of names, still off the shape", () => {
    const outline = Array.from({ length: 6 }, (_, i) =>
      polar(axisAngle(i, 6), 30),
    );
    const names = placeRingNames(6, 90, {
      dots: outline,
      outline,
      names: [cover(90)],
      viewBox: square(90),
    });
    expect(names.map((n) => n.reach)).toEqual(RINGS);
    for (const name of names)
      expect(whatItTouches(boxOf(name), outline, outline, square(90))).toEqual(
        [],
      );
  });

  it("keeps a name that falls back clear of the names placed after it", () => {
    const tooWideForAnySpot: MeasureText = (text, style) =>
      text === TIER_META.INTERESTED.label ? 200 : estimateText(text, style);
    const names = placeRingNames(
      5,
      90,
      {
        dots: [],
        names: [block(-90, -90, 90, 14), block(-90, 29, 90, 90)],
        viewBox: square(90),
      },
      tooWideForAnySpot,
    );
    const boxes = names.map((name) => boxOf(name, tooWideForAnySpot));
    boxes.forEach((box, i) => {
      for (const other of boxes.slice(i + 1))
        expect(apart(box, other)).toBe(true);
    });
  });

  it("keeps a name inside the drawing where no spot qualifies at all", () => {
    const everywhere = Array.from({ length: 21 * 21 }, (_, i): Point => [
      (i % 21) * 5 - 50,
      Math.floor(i / 21) * 5 - 50,
    ]);
    const names = placeRingNames(4, 40, {
      dots: everywhere,
      viewBox: square(40),
    });
    expect(names.map((n) => n.reach)).toEqual(RINGS);
    for (const name of names)
      expect(within(boxOf(name), square(40))).toBe(true);
  });
});
