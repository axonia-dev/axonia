import type { ShapeReach } from "@/features/expertise/domain/practitioner-shape";
import {
  estimateText,
  type MeasureText,
  ringNameStyle,
} from "@/features/expertise/ui/name-style";
import {
  PASSES,
  ringNameAngle,
  type Rules,
  spots,
} from "@/features/expertise/ui/ring-name-spots";
import {
  axisAngle,
  type PlacedLabel,
  polar,
} from "@/features/expertise/ui/shape-layout";
import {
  type Anchor,
  ASCENT,
  type Box,
  boxAt,
  covers,
  crosses,
  inside,
  meets,
  overlaps,
  type Point,
  type Segment,
  shiftInto,
} from "@/features/expertise/ui/text-box";
import { TIER_META } from "@/features/expertise/ui/tier-meta";

export const RINGS: readonly ShapeReach[] = [
  "INTERESTED",
  "CONTRIBUTOR",
  "PRACTITIONER",
];
/** A vertex dot's radius and stroke, and a little air around them. */
const DOT_CLEARANCE = 6.5;
/** Half the outline's stroke and clear air beyond it. */
const OUTLINE_CLEARANCE = 4;
const SPOKE_CLEARANCE = 1.5;

export interface RingName {
  reach: ShapeReach;
  x: number;
  y: number;
  anchor: Anchor;
  fontSize: number;
}

interface Obstacles {
  dots: readonly Point[];
  /** The polygon's vertices, in order. */
  outline?: readonly Point[];
  names?: readonly PlacedLabel[];
  /** The drawing's edge, which no ring name may leave. */
  viewBox: Box;
}

interface Candidate extends RingName {
  box: Box;
  rules: Rules;
  ring: number;
}

interface Scene {
  edge: Box;
  rings: readonly number[];
  dots: readonly Point[];
  outline: readonly Segment[];
  spokes: readonly Segment[];
  boxes: Box[];
}

export function fractionOf(reach: ShapeReach | null): number {
  switch (reach) {
    case "PRACTITIONER":
      return 1;
    case "CONTRIBUTOR":
      return 2 / 3;
    case "INTERESTED":
      return 1 / 3;
    case null:
      return 0;
  }
}

/** What no ring name may ever do: sit on a vertex dot or the outline, or leave the drawing. */
function spoils(box: Box, scene: Scene): boolean {
  return (
    !inside(box, scene.edge) ||
    scene.dots.some((dot) => covers(box, dot, DOT_CLEARANCE)) ||
    scene.outline.some((edge) => crosses(box, edge, OUTLINE_CLEARANCE))
  );
}

/** What only a later pass allows: another ring's circle, a spoke under the halo, another name. */
function crowds({ box, rules, ring }: Candidate, scene: Scene): boolean {
  return (
    (!rules.crossRings &&
      scene.rings.some((other) => other !== ring && meets(box, other))) ||
    (rules.avoidSpokes &&
      scene.spokes.some((spoke) => crosses(box, spoke, SPOKE_CLEARANCE))) ||
    (!rules.overNames && scene.boxes.some((other) => overlaps(box, other)))
  );
}

function sceneOf(count: number, radius: number, obstacles: Obstacles): Scene {
  const outline = obstacles.outline ?? [];
  const names = (obstacles.names ?? [])
    .filter((n) => n.visible)
    .map((n) => ({
      left: n.left,
      right: n.left + n.width,
      top: n.top,
      bottom: n.top + n.height,
    }));
  return {
    edge: obstacles.viewBox,
    rings: RINGS.map((reach) => radius * fractionOf(reach)),
    dots: obstacles.dots,
    outline: outline.map((from, i): Segment => [
      from,
      outline.at((i + 1) % outline.length) ?? from,
    ]),
    spokes: Array.from({ length: count }, (_, i): Segment => [
      [0, 0],
      polar(axisAngle(i, count), radius),
    ]),
    boxes: names,
  };
}

function* candidates(
  reach: ShapeReach,
  count: number,
  radius: number,
  width: (fontSize: number) => number,
): Generator<Candidate> {
  const ring = radius * fractionOf(reach);
  for (const rules of PASSES)
    for (const spot of spots(ring, radius, count, rules)) {
      const { anchor } = spot;
      const { fontSize } = rules;
      const [x, y] = polar(spot.angle, spot.distance);
      const box = boxAt([x, y], width(fontSize), anchor, fontSize);
      yield { reach, x, y, anchor, fontSize, box, rules, ring };
    }
}

/** A ring name's width at each size, measured once however many spots are tried. */
function widthsOf(label: string, measure: MeasureText) {
  const known = new Map<number, number>();
  return (fontSize: number): number => {
    const width =
      known.get(fontSize) ?? measure(label, ringNameStyle(fontSize));
    known.set(fontSize, width);
    return width;
  };
}

/** Where no spot qualifies at all: just inside the ring below the centre, moved within the drawing. */
function fallback(
  reach: ShapeReach,
  count: number,
  scene: Scene,
  width: number,
): RingName {
  const ring = scene.rings[RINGS.indexOf(reach)] ?? 0;
  const [x, y] = polar(ringNameAngle(count), ring - ASCENT * 8);
  const box = boxAt([x, y], width, "middle", 8);
  const { edge } = scene;
  return {
    reach,
    x: x + shiftInto([box.left, box.right], [edge.left, edge.right]),
    y: y + shiftInto([box.top, box.bottom], [edge.top, edge.bottom]),
    anchor: "middle",
    fontSize: 8,
  };
}

/**
 * Names all three rings, never on a vertex dot or the polygon's outline, nor past the drawing's edge. Each takes the
 * first spot that crosses no ring but its own and no name, in the lower gaps, clear of the spokes where there is room,
 * one size down where there is little; failing that, one across another ring; then anywhere on a finer grid; and last,
 * one over another name.
 */
export function placeRingNames(
  count: number,
  radius: number,
  obstacles: Obstacles,
  measure: MeasureText = estimateText,
): RingName[] {
  const scene = sceneOf(count, radius, obstacles);
  return RINGS.map((reach) => {
    const width = widthsOf(TIER_META[reach].label, measure);
    for (const candidate of candidates(reach, count, radius, width)) {
      if (spoils(candidate.box, scene) || crowds(candidate, scene)) continue;
      scene.boxes.push(candidate.box);
      const { x, y, anchor, fontSize } = candidate;
      return { reach, x, y, anchor, fontSize };
    }
    const name = fallback(reach, count, scene, width(8));
    scene.boxes.push(boxAt([name.x, name.y], width(8), name.anchor, 8));
    return name;
  });
}
