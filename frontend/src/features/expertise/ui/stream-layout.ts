import {
  hash,
  type PlacedContribution,
} from "@/features/expertise/ui/contribution-placement";
import {
  buildStream,
  type StreamLayout,
} from "@/features/expertise/ui/stream-bins";
import type { Pan } from "@/features/expertise/ui/terrain-aim";
import type { Spot } from "@/features/expertise/ui/terrain-projection";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * A bin narrower than this holds a handful of contributions, and the difference between three and four of them
 * beats against the bin grid as a sawtooth along the top of the band: a community that contributed at a steady
 * rate is drawn as one that did not. Wide enough that a bin holds a week or so of a real history.
 */
export const PX_PER_BIN = 12;
export const MIN_BINS = 24;
export const MAX_BINS = 240;
export const PAD_Y = 28;

/** The share of a band's own thickness the marks are spread over, leaving its two edges clear so the silhouette
 *  stays the thing that carries the density. */
const SEAT_SPREAD = 0.68;
export const PAD_X = 28;

/** Under {@link MIN_BINS} a history reads as a bar chart, and over {@link MAX_BINS} it is noise nobody can resolve. */
export function binsFor(width: number): number {
  return Math.min(MAX_BINS, Math.max(MIN_BINS, Math.round(width / PX_PER_BIN)));
}

/**
 * The history is inset by {@link PAD_X} at both ends, by the same clearance {@link PAD_Y} leaves above and below.
 * Run edge to edge the bands have no beginning and no end, so the drawing reads as a texture rather than a history,
 * and the axis has nowhere to stand its two dates.
 */
export function xAt(fraction: number, size: GraphSize): number {
  return (fraction - 0.5) * (size.width - 2 * PAD_X);
}

/** Where a bin's edge stands along the history. A single-bin history has no span, so it sits in the middle. */
export function xOf(bin: number, bins: number, size: GraphSize): number {
  return xAt(bins === 1 ? 0.5 : bin / (bins - 1), size);
}

export interface Stacked {
  tag: string;
  slot: number;
  lower: number[];
  upper: number[];
}

/**
 * Every bin's stack is centred on zero rather than piled on a floor, so the shape reads as a thickness over time and
 * no neighbourhood is given the privileged position of being the one at the bottom.
 */
export function stack(layout: StreamLayout, size: GraphSize): Stacked[] {
  const totals = Array.from({ length: layout.bins }, (_, bin) =>
    layout.bands.reduce((sum, band) => sum + band.values[bin]!, 0),
  );
  const peak = Math.max(0, ...totals);
  const scale = peak > 0 ? (size.height - 2 * PAD_Y) / peak : 0;

  const edge = totals.map((total) => (-total * scale) / 2);
  return layout.bands.map((band) => {
    const lower = [...edge];
    const upper = band.values.map((value, bin) => {
      const top = lower[bin]! + value * scale;
      edge[bin] = top;
      return top;
    });
    return { tag: band.tag, slot: band.slot, lower, upper };
  });
}

/**
 * The same contributions, re-seated in stream coordinates: time along x, somewhere across their own band's thickness
 * along y. They carry their own glyph and accent from `markPoints`, so a mark means here what it means on the
 * terrain. Scattered across the thickness rather than piled on the band's middle, because a community with one
 * neighbourhood would otherwise land every mark it has on a single line and draw them as a bar.
 */
export function reseat(
  marks: PlacedContribution[],
  layout: StreamLayout,
  stacked: Stacked[],
  size: GraphSize,
): PlacedContribution[] {
  const span = layout.to - layout.from || 1;
  const bandOf = new Map(stacked.map((band) => [band.tag, band]));

  return marks.flatMap((mark) => {
    const band = bandOf.get(mark.tag);
    if (band === undefined || !Number.isFinite(mark.occurredAt)) return [];
    const fraction = (mark.occurredAt - layout.from) / span;
    const last = band.lower.length - 1;
    const bin = Math.min(last, Math.max(0, Math.round(fraction * last)));
    const lower = band.lower[bin]!;
    const share = 0.5 + SEAT_SPREAD * (hash(mark.signalId) - 0.5);
    return [
      {
        ...mark,
        x: xAt(fraction, size),
        y: lower + (band.upper[bin]! - lower) * share,
      },
    ];
  });
}

/**
 * Where the stream's orthographic camera is aimed and how much of the history it holds. It is sized in pixels, so at
 * rest its clip volume spans exactly the box; zooming narrows it about the point it is aimed at, and panning carries
 * that point across the drawing. The aim is given in the box's own axes - x right, y down - like every other pan, so
 * the vertical is negated here, where the world counts upwards.
 */
export interface StreamCamera {
  zoom: number;
  pan: Pan;
}

export const RESTING_CAMERA: StreamCamera = { zoom: 1, pan: { x: 0, y: 0 } };

export function streamFrustum(
  size: GraphSize,
  camera: StreamCamera,
): { left: number; right: number; top: number; bottom: number } {
  const halfWidth = size.width / (2 * camera.zoom);
  const halfHeight = size.height / (2 * camera.zoom);
  return {
    left: camera.pan.x - halfWidth,
    right: camera.pan.x + halfWidth,
    top: -camera.pan.y + halfHeight,
    bottom: -camera.pan.y - halfHeight,
  };
}

/**
 * Where a world point lands in the box, as fractions of it. This is the camera above and not the inverse of
 * {@link xAt}, so the horizontal inset rides through it untouched: a seat moved inward by PAD_X projects to a spot
 * moved inward by it.
 */
function streamBoxSpot(
  x: number,
  y: number,
  size: GraphSize,
  camera: StreamCamera,
): Spot {
  const { zoom, pan } = camera;
  return {
    left: 0.5 + ((x - pan.x) * zoom) / size.width,
    top: 0.5 - ((y + pan.y) * zoom) / size.height,
  };
}

/**
 * The history actually in frame. A zoomed and panned camera holds a slice of the span, and an axis that went on
 * naming the first and last contribution would be describing a picture the reader is no longer looking at.
 *
 * Clamped at both ends, because the frame may reach past the drawing into the clearance either side of it: the
 * date there is the date of the nearest end of the history, which is what stands at that edge.
 */
export function visibleSpan(
  layout: StreamLayout,
  size: GraphSize,
  camera: StreamCamera,
): { from: number; to: number } {
  const span = layout.to - layout.from;
  const edge = (fraction: number) => {
    const x = camera.pan.x + (fraction - 0.5) * (size.width / camera.zoom);
    const at = x / (size.width - 2 * PAD_X) + 0.5;
    return layout.from + Math.min(1, Math.max(0, at)) * span;
  };
  return { from: edge(0), to: edge(1) };
}

/** Where a point of the stream sits in the box, or null when the camera is not pointed at it. */
export function streamPointOf(
  size: GraphSize,
  camera: StreamCamera,
): (x: number, y: number) => Spot | null {
  return (x, y) => {
    const spot = streamBoxSpot(x, y, size, camera);
    return spot.left < 0 || spot.left > 1 || spot.top < 0 || spot.top > 1
      ? null
      : spot;
  };
}

/**
 * Where a mark's button sits over the stream, as fractions of the box, or null when the stream draws no mark for it
 * or the camera is not pointed at it. The seat comes from {@link reseat} itself rather than from a second copy of
 * its arithmetic, so the button and the glyph cannot drift apart.
 */
export function streamSpotOf(
  marks: PlacedContribution[],
  size: GraphSize,
  camera: StreamCamera,
): (mark: PlacedContribution) => Spot | null {
  const layout = buildStream(marks, binsFor(size.width));
  const seated = new Map(
    reseat(marks, layout, stack(layout, size), size).map((mark) => [
      mark.signalId,
      mark,
    ]),
  );
  const pointOf = streamPointOf(size, camera);

  return (mark) => {
    const at = seated.get(mark.signalId);
    return at === undefined ? null : pointOf(at.x, at.y);
  };
}
