import {
  boardColumn,
  type Topic,
  type TopicState,
} from "@/features/mentoring/domain/mentoring";

export interface Point {
  x: number;
  y: number;
}

export type Orientation = "horizontal" | "vertical";

export interface TrailLayout {
  /** The trailhead, where the pair set out, before the first stop. */
  start: Point;
  stops: Point[];
  /** The whole path, from the trailhead through every stop. */
  path: string;
  /** One curve per stop: the leg that arrives at it. */
  legs: Leg[];
  width: number;
  height: number;
}

export interface Leg {
  from: Point;
  c1: Point;
  c2: Point;
  to: Point;
}

export const MAX_STOPS = 30;
/** The smallest gap kept between two stops, in layout units: a stop and its label. */
export const STOP_SPACING = 120;

const HORIZONTAL = { width: 1000, perRow: 7, rowHeight: 200, margin: 70 };
const STRIP = {
  width: 1000,
  height: 130,
  first: 90,
  last: 960,
  start: { x: 20, y: 96 },
  end: { x: 980, y: 70 },
};
/** Where an empty strip's road bends, so the way ahead still winds. */
const ROAD_BEND = { x: 480, y: 58 };
const VERTICAL = { width: 360, step: 120, left: 96, right: 264, top: 80 };

/** Done first, then In progress, then To do, each in its board order. */
export function trailOrder(topics: readonly Topic[]): Topic[] {
  return [
    ...boardColumn(topics, "DONE"),
    ...boardColumn(topics, "IN_PROGRESS"),
    ...boardColumn(topics, "TODO"),
  ];
}

/** Where the pair stands: the first stop in progress, else the last one done, else the first. */
export function standingIndex(topics: readonly Topic[]): number {
  const ordered = trailOrder(topics);
  const inProgress = ordered.findIndex(
    (topic) => topic.state === "IN_PROGRESS",
  );
  if (inProgress >= 0) return inProgress;
  const done = ordered.filter((topic) => topic.state === "DONE").length;
  return Math.max(0, done - 1);
}

/** The same standing as `standingIndex`, read from stops already in walking order. */
export function standingOfStops(stops: readonly TopicState[]): number {
  const inProgress = stops.indexOf("IN_PROGRESS");
  if (inProgress >= 0) return inProgress;
  return Math.max(0, stops.lastIndexOf("DONE"));
}

/** Rows run left to right, then right to left, so the path winds back on itself like a switchback. */
function horizontalStops(count: number): { stops: Point[]; height: number } {
  const { width, perRow, rowHeight, margin } = HORIZONTAL;
  const rows = Math.max(1, Math.ceil(count / perRow));
  const across = Math.min(count, perRow);
  const gap = across > 1 ? (width - 2 * margin) / (perRow - 1) : 0;
  const offset = across < perRow ? ((perRow - across) * gap) / 2 : 0;
  const stops = Array.from({ length: count }, (_, index) => {
    const row = Math.floor(index / perRow);
    const column = index % perRow;
    const along = row % 2 === 0 ? column : perRow - 1 - column;
    const wobble = index % 2 === 0 ? 34 : -34;
    return {
      x: margin + offset + along * gap,
      y: 90 + row * rowHeight + wobble,
    };
  });
  return { stops, height: rows * rowHeight + 30 };
}

function verticalStops(count: number): { stops: Point[]; height: number } {
  const { step, left, right, top } = VERTICAL;
  const stops = Array.from({ length: count }, (_, index) => ({
    x: index % 2 === 0 ? left : right,
    y: top + 40 + index * step,
  }));
  return { stops, height: top + 40 + Math.max(0, count - 1) * step + 90 };
}

function trailhead(first: Point | undefined, orientation: Orientation): Point {
  if (first === undefined) return { x: 30, y: 60 };
  return orientation === "horizontal"
    ? { x: 20, y: first.y + 40 }
    : { x: VERTICAL.width / 2, y: 20 };
}

/** A smooth curve through every point: each leg's handles follow the line between its neighbours. */
function legsThrough(points: readonly Point[]): Leg[] {
  return points.slice(1).map((to, index) => {
    const from = points[index]!;
    const before = points[index - 1] ?? from;
    const after = points[index + 2] ?? to;
    return {
      from,
      c1: {
        x: from.x + (to.x - before.x) / 6,
        y: from.y + (to.y - before.y) / 6,
      },
      c2: {
        x: to.x - (after.x - from.x) / 6,
        y: to.y - (after.y - from.y) / 6,
      },
      to,
    };
  });
}

export function pathOf(start: Point, legs: readonly Leg[]): string {
  const curves = legs.map(
    ({ c1, c2, to }) => `C ${c1.x} ${c1.y}, ${c2.x} ${c2.y}, ${to.x} ${to.y}`,
  );
  return [`M ${start.x} ${start.y}`, ...curves].join(" ");
}

/** A winding path through `count` stops, at most thirty, laid out the same way every time. */
export function trailLayout(
  count: number,
  orientation: Orientation,
): TrailLayout {
  const shown = Math.max(0, Math.min(count, MAX_STOPS));
  const { stops, height } =
    orientation === "horizontal"
      ? horizontalStops(shown)
      : verticalStops(shown);
  const start = trailhead(stops[0], orientation);
  const legs = legsThrough([start, ...stops]);
  return {
    start,
    stops,
    path: pathOf(start, legs),
    legs,
    width: orientation === "horizontal" ? HORIZONTAL.width : VERTICAL.width,
    height,
  };
}

/** One row for a card: the trailhead at the left edge, the stops spread evenly with a wobble, a lone stop centred. */
export function stripLayout(count: number): TrailLayout {
  const { width, height, first, last, start, end } = STRIP;
  const shown = Math.max(0, Math.min(count, MAX_STOPS));
  const gap = shown > 1 ? (last - first) / (shown - 1) : 0;
  const left = shown === 1 ? (first + last) / 2 : first;
  const stops = Array.from({ length: shown }, (_, index) => ({
    x: left + index * gap,
    y: index % 2 === 0 ? 64 : 104,
  }));
  const legs = legsThrough([start, ...stops]);
  const road = shown === 0 ? legsThrough([start, ROAD_BEND, end]) : legs;
  return { start, stops, path: pathOf(start, road), legs, width, height };
}

function pointOn({ from, c1, c2, to }: Leg, t: number): Point {
  const u = 1 - t;
  const a = u * u * u;
  const b = 3 * u * u * t;
  const c = 3 * u * t * t;
  const d = t * t * t;
  return {
    x: a * from.x + b * c1.x + c * c2.x + d * to.x,
    y: a * from.y + b * c1.y + c * c2.y + d * to.y,
  };
}

/** `count` prints spread evenly along the legs walked so far, stepping left and right of the path. */
export function footprints(walked: readonly Leg[], count: number): Point[] {
  if (walked.length === 0 || count === 0) return [];
  return Array.from({ length: count }, (_, index) => {
    const along = ((index + 0.5) / count) * walked.length;
    const leg = walked[Math.min(walked.length - 1, Math.floor(along))]!;
    const t = along - Math.floor(along);
    const here = pointOn(leg, t);
    const ahead = pointOn(leg, Math.min(1, t + 0.01));
    const length = Math.hypot(ahead.x - here.x, ahead.y - here.y) || 1;
    const side = index % 2 === 0 ? 6 : -6;
    return {
      x: here.x - ((ahead.y - here.y) / length) * side,
      y: here.y + ((ahead.x - here.x) / length) * side,
    };
  });
}
