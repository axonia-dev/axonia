export type Point = readonly [number, number];
export type Segment = readonly [Point, Point];
export type Anchor = "start" | "middle" | "end";

/** How far above and below its baseline a line of text reaches, in em. */
export const ASCENT = 0.78;
export const DESCENT = 0.22;
/** The air kept between a text and a line or another text. */
const LINE_CLEARANCE = 2;

export interface Box {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

function leftOf(x: number, width: number, anchor: Anchor): number {
  switch (anchor) {
    case "start":
      return x;
    case "end":
      return x - width;
    case "middle":
      return x - width / 2;
  }
}

export function boxAt(
  point: Point,
  width: number,
  anchor: Anchor,
  size: number,
): Box {
  const [x, y] = point;
  const left = leftOf(x, width, anchor);
  return {
    left,
    right: left + width,
    top: y - ASCENT * size,
    bottom: y + DESCENT * size,
  };
}

export function covers(box: Box, [cx, cy]: Point, clearance: number): boolean {
  const nearestX = Math.max(box.left, Math.min(cx, box.right));
  const nearestY = Math.max(box.top, Math.min(cy, box.bottom));
  return Math.hypot(cx - nearestX, cy - nearestY) <= clearance;
}

function clip(range: [number, number], p: number, q: number): boolean {
  if (p === 0) return q >= 0;
  const t = q / p;
  if (p < 0) range[0] = Math.max(range[0], t);
  else range[1] = Math.min(range[1], t);
  return range[0] <= range[1];
}

/** Whether a segment passes within `clearance` of a box (Liang–Barsky against the grown box). */
export function crosses(
  box: Box,
  [[x0, y0], [x1, y1]]: Segment,
  clearance: number,
) {
  const range: [number, number] = [0, 1];
  const dx = x1 - x0;
  const dy = y1 - y0;
  return (
    clip(range, -dx, x0 - (box.left - clearance)) &&
    clip(range, dx, box.right + clearance - x0) &&
    clip(range, -dy, y0 - (box.top - clearance)) &&
    clip(range, dy, box.bottom + clearance - y0)
  );
}

export function overlaps(a: Box, b: Box): boolean {
  return (
    a.left < b.right + LINE_CLEARANCE &&
    b.left < a.right + LINE_CLEARANCE &&
    a.top < b.bottom + LINE_CLEARANCE &&
    b.top < a.bottom + LINE_CLEARANCE
  );
}

/** Whether a ring's circle passes through the box: some of it lies inside the circle and some outside. */
export function meets(box: Box, circle: number): boolean {
  const corners = [box.left, box.right].flatMap((x) =>
    [box.top, box.bottom].map((y) => Math.hypot(x, y)),
  );
  return (
    covers(box, [0, 0], circle + LINE_CLEARANCE) &&
    Math.max(...corners) > circle - LINE_CLEARANCE
  );
}

export function inside(box: Box, bounds: Box): boolean {
  return (
    box.left >= bounds.left &&
    box.right <= bounds.right &&
    box.top >= bounds.top &&
    box.bottom <= bounds.bottom
  );
}

/** How far a span must move along its axis to lie within `[low, high]`, or to start at `low` where it is wider. */
export function shiftInto(
  [from, to]: readonly [number, number],
  [low, high]: readonly [number, number],
): number {
  if (from < low) return low - from;
  return Math.min(0, high - to);
}
