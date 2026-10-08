import type { LabelSeat, MapLabel } from "@/features/expertise/ui/map-labels";
import type { Disc } from "@/features/expertise/ui/terrain-camera";
import type { Spot } from "@/features/expertise/ui/terrain-projection";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * A name's box, estimated from the string, since measuring would reflow per name per frame: each font's average advance
 * per character as measured in a browser, and the padding and line heights of the boxes `MapLabelLayer` draws.
 */
const BOX: Record<
  MapLabel["kind"],
  { charWidth: number; padX: number; halfHeight: number }
> = {
  named: { charWidth: 6.5, padX: 9, halfHeight: 11 },
  neighbourhood: { charWidth: 8.4, padX: 0, halfHeight: 8 },
  area: { charWidth: 5.8, padX: 0, halfHeight: 8 },
};

/** How far an area's name stands clear of the marks piled on the area itself. */
const AREA_LIFT = 11;

/** Clear air around a name, so two that merely miss each other still read as two. */
const GUTTER = 4;

/** A name that has found room, in box pixels rather than fractions: a text box is measured in pixels either way. */
export interface PlacedLabel {
  label: MapLabel;
  /** The seat it took, which is what says whether a reading lights it. */
  seat: LabelSeat;
  x: number;
  y: number;
}

interface Box {
  x: number;
  y: number;
  hw: number;
  hh: number;
}

function boxOf(label: MapLabel, spot: Spot, size: GraphSize): Box {
  const { charWidth, padX, halfHeight } = BOX[label.kind];
  return {
    x: spot.left * size.width,
    y: spot.top * size.height - (label.kind === "area" ? AREA_LIFT : 0),
    hw: (label.text.length * charWidth) / 2 + padX + GUTTER,
    hh: halfHeight + GUTTER,
  };
}

function overlaps(a: Box, b: Box): boolean {
  return Math.abs(a.x - b.x) < a.hw + b.hw && Math.abs(a.y - b.y) < a.hh + b.hh;
}

function inside(box: Box, size: GraphSize): boolean {
  return (
    box.x - box.hw >= 0 &&
    box.x + box.hw <= size.width &&
    box.y - box.hh >= 0 &&
    box.y + box.hh <= size.height
  );
}

/** Every corner, since a box can leave a disc at a corner while each of its edges' midpoints is still inside it. */
function onDisc(box: Box, disc: Disc): boolean {
  return [-1, 1].every((sx) =>
    [-1, 1].every(
      (sy) =>
        Math.hypot(
          box.x + sx * box.hw - disc.x,
          box.y + sy * box.hh - disc.y,
        ) <= disc.r,
    ),
  );
}

/**
 * The names that actually fit, in the order they were ranked. A name is dropped rather than moved, shrunk or elided:
 * a map that crams every word in is unreadable, and a name nudged off the thing it names is worse than no name. What
 * a dense picture drops comes back as the reader zooms, because zooming spreads the very spots this measures.
 *
 * On the globe a name must also stay on the planet: past the limb it would stand on the page, over nothing it names.
 * Its seat facing the camera is not enough, because the word is wider than the point it stands on.
 */
export function placeLabels(
  labels: MapLabel[],
  pointOf: (x: number, y: number) => Spot | null,
  size: GraphSize,
  disc: Disc | null = null,
): PlacedLabel[] {
  const taken: Box[] = [];
  const placed: PlacedLabel[] = [];

  for (const label of labels) {
    for (const seat of label.at) {
      const spot = pointOf(seat.x, seat.y);
      if (spot === null) continue;

      const box = boxOf(label, spot, size);
      const fits = inside(box, size) && (disc === null || onDisc(box, disc));
      if (!fits || taken.some((other) => overlaps(other, box))) continue;
      taken.push(box);
      placed.push({ label, seat, x: box.x, y: box.y });
      break;
    }
  }

  return placed;
}
