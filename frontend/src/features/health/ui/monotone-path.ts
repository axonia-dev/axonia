interface Point {
  x: number;
  y: number;
}

function sign(value: number): number {
  return value < 0 ? -1 : 1;
}

/** Fritsch and Carlson's tangents, as d3's monotone curve uses them. */
function tangents(points: Point[]): number[] {
  const slopes = points
    .slice(1)
    .map(
      (point, index) =>
        (point.y - points[index]!.y) / (point.x - points[index]!.x),
    );
  const inner = points.map((_, index) => {
    const before = slopes[index - 1];
    const after = slopes[index];
    if (before === undefined || after === undefined) return 0;
    if (before * after <= 0) return 0;
    return (
      (sign(before) + sign(after)) *
      Math.min(Math.abs(before), Math.abs(after)) *
      0.5
    );
  });
  const last = points.length - 1;
  inner[0] = slopes.length === 0 ? 0 : (3 * slopes[0]! - inner[1]!) / 2;
  inner[last] =
    slopes.length === 0 ? 0 : (3 * slopes[last - 1]! - inner[last - 1]!) / 2;
  return inner;
}

function clamp(value: number, a: number, b: number): number {
  return Math.min(Math.max(value, Math.min(a, b)), Math.max(a, b));
}

/** A smooth path through every point that never overshoots one: a zero stays on the baseline. */
export function monotonePath(points: Point[]): string {
  if (points.length === 0) return "";
  const slopeAt = tangents(points);
  const [first, ...rest] = points;
  const start = `M${first!.x.toFixed(1)},${first!.y.toFixed(1)}`;
  const segments = rest.map((point, index) => {
    const from = points[index]!;
    const third = (point.x - from.x) / 3;
    const c1 = clamp(from.y + slopeAt[index]! * third, from.y, point.y);
    const c2 = clamp(point.y - slopeAt[index + 1]! * third, from.y, point.y);
    return `C${(from.x + third).toFixed(1)},${c1.toFixed(1)} ${(point.x - third).toFixed(1)},${c2.toFixed(1)} ${point.x.toFixed(1)},${point.y.toFixed(1)}`;
  });
  return [start, ...segments].join(" ");
}
