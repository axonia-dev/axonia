import { axisAngle, NAME_GAP } from "@/features/expertise/ui/shape-layout";
import { type Anchor, ASCENT, DESCENT } from "@/features/expertise/ui/text-box";

/** The last passes' finer grid: this many directions round the drawing, and this step along each. */
const FINE_ANGLES = 72;
const FINE_STEP = 0.5;

export interface Rules {
  fontSize: number;
  avoidSpokes: boolean;
  /** The lower half's gaps, every gap, or any direction at all on the finer grid. */
  where: "lowerGaps" | "anyGap" | "anywhere";
  /** Across another ring's circle; its own a name may always cross. */
  crossRings: boolean;
  overNames: boolean;
}

const CLEAR = { crossRings: false, overNames: false } as const;
const ACROSS_RINGS = { crossRings: true, overNames: false } as const;
const OVER_NAMES = { crossRings: true, overNames: true } as const;

/**
 * A clear spot first: clear of every spoke, then across one under the name's halo, then one size down, then in any
 * gap. Then a spot across another ring's circle; then anywhere on a finer grid; and only then over another name.
 */
export const PASSES: readonly Rules[] = [
  { ...CLEAR, fontSize: 9, avoidSpokes: true, where: "lowerGaps" },
  { ...CLEAR, fontSize: 9, avoidSpokes: false, where: "lowerGaps" },
  { ...CLEAR, fontSize: 8, avoidSpokes: true, where: "lowerGaps" },
  { ...CLEAR, fontSize: 8, avoidSpokes: false, where: "lowerGaps" },
  { ...CLEAR, fontSize: 8, avoidSpokes: false, where: "anyGap" },
  { ...ACROSS_RINGS, fontSize: 9, avoidSpokes: false, where: "lowerGaps" },
  { ...ACROSS_RINGS, fontSize: 8, avoidSpokes: false, where: "anyGap" },
  { ...ACROSS_RINGS, fontSize: 8, avoidSpokes: false, where: "anywhere" },
  { ...OVER_NAMES, fontSize: 8, avoidSpokes: false, where: "anywhere" },
];

interface Spot {
  angle: number;
  anchor: Anchor;
  distance: number;
}

/** The empty gap nearest the bottom: straight down between two spokes, or beside the bottom spoke of an even count. */
export function ringNameAngle(count: number): number {
  return Math.PI / 2 + (count % 2 === 0 ? Math.PI / count : 0);
}

/** Where in a gap a name is tried, in eighths of the gap either side of its bisector, the bisector first. */
const GAP_OFFSETS = [0, -1, 1, -2, 2, -3, 3];

/**
 * Every gap between two spokes in the lower half, the one nearest the bottom first; within each, its bisector
 * first and then further towards either spoke.
 */
function gapAngles(count: number, anyGap: boolean): number[] {
  const first = ringNameAngle(count);
  const gap = (2 * Math.PI) / count;
  return Array.from({ length: count }, (_, i) => axisAngle(i, count) + gap / 2)
    .filter((angle) => anyGap || Math.sin(angle) > 0.2)
    .sort((a, b) => Math.abs(a - first) - Math.abs(b - first))
    .flatMap((bisector) =>
      GAP_OFFSETS.map((offset) => bisector + (offset * gap) / 8),
    );
}

/** Every direction round the drawing, the one nearest the bottom gap first. */
function allAngles(count: number): number[] {
  const first = ringNameAngle(count);
  const step = (2 * Math.PI) / FINE_ANGLES;
  return Array.from(
    { length: FINE_ANGLES },
    (_, i) => first + Math.ceil(i / 2) * (i % 2 === 0 ? 1 : -1) * step,
  );
}

function anchorsFor(angle: number): Anchor[] {
  const cos = Math.cos(angle);
  if (cos < -0.05) return ["end", "middle"];
  if (cos > 0.05) return ["start", "middle"];
  return ["middle"];
}

function directions(count: number, rules: Rules): [number, Anchor][] {
  const angles =
    rules.where === "anywhere"
      ? allAngles(count)
      : gapAngles(count, rules.where === "anyGap");
  return angles.flatMap((angle) =>
    anchorsFor(angle).map((anchor): [number, Anchor] => [angle, anchor]),
  );
}

/** How far past its ring a name may reach: across it, and once crossing a spoke is allowed, out to the next ring or the names. */
function farthest(ring: number, radius: number, rules: Rules): number {
  const descent = DESCENT * rules.fontSize;
  if (rules.avoidSpokes) return ring + descent;
  if (ring >= radius - 1e-6) return radius + NAME_GAP;
  return ring + radius / 3 - ASCENT * rules.fontSize - descent;
}

/** From the ring inside it out to `farthest`; across other rings, from the centre to the outer ring. */
function band(ring: number, radius: number, rules: Rules): [number, number] {
  const ascent = ASCENT * rules.fontSize;
  if (rules.crossRings) return [ascent, radius];
  return [
    Math.max(ascent, ring - radius / 3 + ascent),
    farthest(ring, radius, rules),
  ];
}

/** Every baseline in the band, the one just inside the ring first, then outward from it. */
function distances(ring: number, radius: number, rules: Rules): number[] {
  const home = ring - ASCENT * rules.fontSize;
  const [from, to] = band(ring, radius, rules);
  const step = rules.where === "anywhere" ? FINE_STEP : 1;
  const count = Math.max(0, Math.floor((to - from) / step) + 1);
  return Array.from({ length: count }, (_, i) => from + i * step).sort(
    (a, b) => Math.abs(a - home) - Math.abs(b - home),
  );
}

/** In a gap, every distance along one direction before the next; on the finer grid, every direction round one ring. */
export function* spots(
  ring: number,
  radius: number,
  count: number,
  rules: Rules,
): Generator<Spot> {
  const around = directions(count, rules);
  const along = distances(ring, radius, rules);
  if (rules.where === "anywhere")
    for (const distance of along)
      for (const [angle, anchor] of around) yield { angle, anchor, distance };
  else
    for (const [angle, anchor] of around)
      for (const distance of along) yield { angle, anchor, distance };
}
