import type { ShapeReach } from "@/features/expertise/domain/practitioner-shape";
import {
  estimateText,
  type MeasureText,
  ringNameStyle,
} from "@/features/expertise/ui/name-style";
import {
  fractionOf,
  type RingName,
  RINGS,
} from "@/features/expertise/ui/ring-names";
import type { PlacedLabel } from "@/features/expertise/ui/shape-layout";
import { TIER_META } from "@/features/expertise/ui/tier-meta";

export type Point = readonly [number, number];

export interface Box {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

export function boxOf(
  name: RingName,
  measure: MeasureText = estimateText,
): Box {
  const width = measure(
    TIER_META[name.reach].label,
    ringNameStyle(name.fontSize),
  );
  const shift = { start: 0, middle: width / 2, end: width }[name.anchor];
  return {
    left: name.x - shift,
    right: name.x - shift + width,
    top: name.y - 0.78 * name.fontSize,
    bottom: name.y + 0.22 * name.fontSize,
  };
}

export function distanceTo(box: Box, [x, y]: Point): number {
  const nearestX = Math.max(box.left, Math.min(x, box.right));
  const nearestY = Math.max(box.top, Math.min(y, box.bottom));
  return Math.hypot(x - nearestX, y - nearestY);
}

export function crosses(box: Box, from: Point, to: Point): boolean {
  return Array.from({ length: 401 }, (_, i) => i / 400).some(
    (t) =>
      distanceTo(box, [
        from[0] + (to[0] - from[0]) * t,
        from[1] + (to[1] - from[1]) * t,
      ]) < 1,
  );
}

export function apart(a: Box, b: Box): boolean {
  return (
    a.right <= b.left ||
    b.right <= a.left ||
    a.bottom <= b.top ||
    b.bottom <= a.top
  );
}

export function within(box: Box, bounds: Box): boolean {
  return (
    box.left >= bounds.left &&
    box.right <= bounds.right &&
    box.top >= bounds.top &&
    box.bottom <= bounds.bottom
  );
}

function meetsCircle(box: Box, radius: number): boolean {
  const nearest = distanceTo(box, [0, 0]);
  const farthest = Math.max(
    ...[box.left, box.right].flatMap((x) =>
      [box.top, box.bottom].map((y) => Math.hypot(x, y)),
    ),
  );
  return nearest < radius && farthest > radius;
}

export function otherRingsMet(name: RingName, radius: number): ShapeReach[] {
  return RINGS.filter(
    (ring) =>
      ring !== name.reach &&
      meetsCircle(boxOf(name), radius * fractionOf(ring)),
  );
}

export function labelBoxes(labels: readonly PlacedLabel[]): Box[] {
  return labels
    .filter((l) => l.visible)
    .map((l) => ({
      left: l.left,
      right: l.left + l.width,
      top: l.top,
      bottom: l.top + l.height,
    }));
}

/** The outer ring's square, a drawing's viewBox at its tightest. */
export function square(radius: number): Box {
  return { left: -radius, right: radius, top: -radius, bottom: radius };
}

/** What a ring name's box sits on: a vertex dot, an edge of the outline, or the viewBox's edge. */
export function whatItTouches(
  box: Box,
  dots: readonly Point[],
  outline: readonly Point[],
  view: Box,
): string[] {
  const edges = outline.map((from, i): [Point, Point] => [
    from,
    outline.at((i + 1) % outline.length)!,
  ]);
  return [
    ...(dots.some((dot) => distanceTo(box, dot) <= 6) ? ["dot"] : []),
    ...(edges.some(([from, to]) => crosses(box, from, to)) ? ["outline"] : []),
    ...(within(box, view) ? [] : ["edge"]),
  ];
}
