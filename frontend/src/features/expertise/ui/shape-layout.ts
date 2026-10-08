import type { AxisKind } from "@/features/expertise/domain/practitioner-shape";
import {
  estimateText,
  type MeasureText,
  nameStyle,
} from "@/features/expertise/ui/name-style";
import { keepClear, TOLERANCE } from "@/features/expertise/ui/shape-clearance";
import { wrapName } from "@/features/expertise/ui/shape-name-wrap";
import type { Box } from "@/features/expertise/ui/text-box";

export const LINE_HEIGHT = 13;
export const MARK_WIDTH = 16;
/** How far past the outermost ring a name begins. */
export const NAME_GAP = 10;
const MIN_RADIUS = 40;
/** The drawing never shrinks below this share of its width, whatever its names need. */
const FLOOR_SHARE = 0.22;
const MAX_RADIUS = 150;
const SIDE = 0.2;
const PAD = 12;
const MAX_LINE = 170;
const SEARCH_STEPS = 12;

export interface LabelSpec {
  text: string;
  kind: AxisKind;
  marks: number;
  reached: boolean;
}

type Anchor = "start" | "middle" | "end";

export interface PlacedLabel {
  lines: string[];
  anchor: Anchor;
  /** Where every line is anchored. */
  textX: number;
  /** The left edge of the marks, which sit beside line `marksLine`, or alone on it when it is past the last line. */
  marksX: number;
  marksLine: number;
  top: number;
  left: number;
  width: number;
  height: number;
  visible: boolean;
}

export interface ShapeLayout {
  radius: number;
  width: number;
  height: number;
  centreY: number;
  labels: PlacedLabel[];
}

interface Spoke {
  spec: LabelSpec;
  angle: number;
  anchor: Anchor;
  measure: (text: string) => number;
}

interface Block {
  lines: string[];
  widest: number;
  /** The width of the text the marks sit beside: none when they have a line of their own. */
  last: number;
  marks: number;
  ownLine: boolean;
  cut: boolean;
}

interface Extent {
  text: number;
  marks: number;
  left: number;
  right: number;
}

export function viewBoxOf({ width, height, centreY }: ShapeLayout): Box {
  return {
    left: -width / 2,
    right: width / 2,
    top: -centreY,
    bottom: height - centreY,
  };
}

export function axisAngle(index: number, count: number): number {
  return -Math.PI / 2 + (index * 2 * Math.PI) / count;
}

export function polar(angle: number, distance: number): [number, number] {
  return [Math.cos(angle) * distance, Math.sin(angle) * distance];
}

function anchorOf(cos: number): Anchor {
  if (Math.abs(cos) < SIDE) return "middle";
  return cos > 0 ? "start" : "end";
}

/** The widest a line may be on this spoke at this radius and stay inside the width; a top or bottom name has it all. */
function capAt(spoke: Spoke, radius: number, width: number): number {
  const room =
    width / 2 - (radius + NAME_GAP) * Math.abs(Math.cos(spoke.angle));
  const marks = spoke.spec.marks * MARK_WIDTH;
  return Math.min(
    MAX_LINE,
    spoke.anchor === "middle" ? 2 * room - marks : room,
  );
}

function blockAt(spoke: Spoke, radius: number, width: number): Block {
  const marks = spoke.spec.marks * MARK_WIDTH;
  const wrapped = wrapName(
    spoke.spec.text,
    capAt(spoke, radius, width),
    marks,
    spoke.measure,
  );
  const widths = wrapped.lines.map((l) => spoke.measure(l));
  return {
    lines: wrapped.lines,
    widest: Math.max(0, ...widths),
    last: wrapped.marksOwnLine ? 0 : (widths.at(-1) ?? 0),
    marks,
    ownLine: wrapped.marksOwnLine,
    cut: wrapped.cut,
  };
}

function middleExtent({ widest, last, marks, ownLine }: Block): Extent {
  if (ownLine) {
    const half = Math.max(widest, marks) / 2;
    return { text: 0, marks: -marks / 2, left: -half, right: half };
  }
  return {
    text: -marks / 2,
    marks: (last - marks) / 2,
    left: -(widest + marks) / 2,
    right: Math.max(widest - marks, last + marks) / 2,
  };
}

/** Where the text, the marks and the box's edges sit, relative to the point a spoke anchors its name at. */
function extentOf(block: Block, anchor: Anchor): Extent {
  const { widest, last, marks } = block;
  const span = Math.max(widest, last + marks);
  switch (anchor) {
    case "start":
      return { text: 0, marks: last, left: 0, right: span };
    case "end":
      return { text: 0, marks: -last - marks, left: -span, right: 0 };
    case "middle":
      return middleExtent(block);
  }
}

function withinWidth(
  spoke: Spoke,
  block: Block,
  radius: number,
  width: number,
) {
  const x = (radius + NAME_GAP) * Math.cos(spoke.angle);
  const { left, right } = extentOf(block, spoke.anchor);
  return (
    x + left >= -width / 2 - TOLERANCE && x + right <= width / 2 + TOLERANCE
  );
}

/** A name whole at the floor stays whole; one cut there keeps what it shows there; either stays inside the width. */
function holds(spoke: Spoke, floor: Block, radius: number, width: number) {
  const block = blockAt(spoke, radius, width);
  const kept = floor.cut
    ? block.lines.join("\n") === floor.lines.join("\n")
    : !block.cut;
  return kept && withinWidth(spoke, block, radius, width);
}

/** The largest radius at which every name holds, from the floor up to the ceiling. */
function fitRadius(spokes: readonly Spoke[], width: number): number {
  const ceiling = Math.min(MAX_RADIUS, width / 2);
  let low = Math.min(ceiling, Math.max(MIN_RADIUS, width * FLOOR_SHARE));
  const atFloor = spokes.map((s) => [s, blockAt(s, low, width)] as const);
  const fits = (radius: number) =>
    atFloor.every(([s, floor]) => holds(s, floor, radius, width));
  if (fits(ceiling)) return ceiling;
  let high = ceiling;
  for (let step = 0; step < SEARCH_STEPS; step++) {
    const middle = (low + high) / 2;
    if (fits(middle)) low = middle;
    else high = middle;
  }
  return low;
}

function topOf(sin: number, distance: number, height: number): number {
  const y = distance * sin;
  if (sin < -SIDE) return y - height;
  if (sin > SIDE) return y;
  return y - height / 2;
}

function place(block: Block, angle: number, radius: number): PlacedLabel {
  const distance = radius + NAME_GAP;
  const cos = Math.cos(angle);
  const anchor = anchorOf(cos);
  const extent = extentOf(block, anchor);
  const x = distance * cos;
  const rows = block.lines.length + Number(block.ownLine);
  return {
    lines: block.lines,
    anchor,
    textX: x + extent.text,
    marksX: x + extent.marks,
    marksLine: rows - 1,
    top: topOf(Math.sin(angle), distance, rows * LINE_HEIGHT),
    left: x + extent.left,
    width: extent.right - extent.left,
    height: rows * LINE_HEIGHT,
    visible: true,
  };
}

function extentOfAll(labels: readonly PlacedLabel[], radius: number) {
  const shown = labels.filter((l) => l.visible);
  const top = Math.min(-radius, ...shown.map((l) => l.top));
  const bottom = Math.max(radius, ...shown.map((l) => l.top + l.height));
  return { top, bottom };
}

/** Names keep their size and stay whole where they can; the radius is the largest that allows it. */
export function layoutShape(
  specs: readonly LabelSpec[],
  width: number,
  measure: MeasureText = estimateText,
): ShapeLayout {
  const spokes = specs.map((spec, i): Spoke => {
    const angle = axisAngle(i, specs.length);
    const style = nameStyle(spec.kind, width);
    return {
      spec,
      angle,
      anchor: anchorOf(Math.cos(angle)),
      measure: (text) => measure(text, style),
    };
  });
  const radius = fitRadius(spokes, width);
  const labels = spokes.map((spoke) =>
    place(blockAt(spoke, radius, width), spoke.angle, radius),
  );
  keepClear(labels, specs);
  const { top, bottom } = extentOfAll(labels, radius);
  return {
    radius,
    width,
    height: bottom - top + 2 * PAD,
    centreY: -top + PAD,
    labels,
  };
}
