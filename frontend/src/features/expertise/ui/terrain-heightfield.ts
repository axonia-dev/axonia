import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

export interface Heightfield {
  cols: number;
  rows: number;
  cell: number;
  values: Float32Array;
}

/** The grid the ground is built on, in layout pixels. Finer than the eye needs at rest, because zooming magnifies
 *  the cell along with everything else and a coastline is where a cell's own edges start to show. */
const CELL_PX = 6;

/** Past this many cells from a contribution the gaussian is under a thousandth of its peak: cheaper to treat as zero. */
const KERNEL_CELLS = 5;
const SIGMA_CELLS = 1.9;

/**
 * How many overlapping kernels it takes to reach about two thirds of the maximum height. It sets where the coast
 * falls: a lone contribution piles up one kernel and comes out at 0.36, just above the shoreline the shading module
 * draws at 0.3, so one contribution is a small island and two together are a hill.
 */
const SATURATION = 2.2;

/** How much the ground wanders either side of what the kernels alone would give, as a share of the full height. */
const RIPPLE = 0.045;

/**
 * How far in from the plane's border the wandering fades out, in layout pixels. Beyond the plane is the deepest water
 * and nothing else: the flat reading is cleared to it, and the globe's poles are capped with it and its two side
 * edges meet round the back. A border whose sea wanders shows as a hard line in all three places.
 */
const SHORELESS_BORDER_PX = 48;

/**
 * A little smooth wandering, so a coast is a coast rather than the outline of a gaussian and the open sea has a
 * floor. Two incommensurable periods, which is enough that the pattern never repeats within a plane and costs two
 * sines per cell; the argument is in layout pixels, so the globe and the flat reading wander identically.
 */
function ripple(x: number, y: number): number {
  return (
    Math.sin(x * 0.031 + 1.3) * Math.cos(y * 0.027) +
    Math.sin((x + y) * 0.019 + 2.1)
  );
}

function smoothstep(value: number): number {
  const at = Math.min(1, Math.max(0, value));
  return at * at * (3 - 2 * at);
}

function borderFade(x: number, y: number, size: GraphSize): number {
  const nearest = Math.min(x, y, size.width - x, size.height - y);
  return smoothstep(nearest / SHORELESS_BORDER_PX);
}

export function buildHeightfield(
  marks: PlacedContribution[],
  size: GraphSize,
): Heightfield {
  const cols = Math.max(1, Math.ceil(size.width / CELL_PX) + 1);
  const rows = Math.max(1, Math.ceil(size.height / CELL_PX) + 1);
  const values = new Float32Array(cols * rows);
  const twoSigmaSquared = 2 * SIGMA_CELLS * SIGMA_CELLS;

  for (const mark of marks) {
    const cx = mark.x / CELL_PX;
    const cy = mark.y / CELL_PX;
    const fromCol = Math.max(0, Math.floor(cx - KERNEL_CELLS));
    const toCol = Math.min(cols - 1, Math.ceil(cx + KERNEL_CELLS));
    const fromRow = Math.max(0, Math.floor(cy - KERNEL_CELLS));
    const toRow = Math.min(rows - 1, Math.ceil(cy + KERNEL_CELLS));

    for (let row = fromRow; row <= toRow; row++) {
      for (let col = fromCol; col <= toCol; col++) {
        const dx = col - cx;
        const dy = row - cy;
        values[row * cols + col]! += Math.exp(
          -(dx * dx + dy * dy) / twoSigmaSquared,
        );
      }
    }
  }

  // Saturated rather than normalised against the field's own peak, and that is the difference between a map and a
  // dark blue nothing. The raw sum is unbounded - at five thousand contributions the ground would stand twenty
  // times higher than the globe it is wrapped on - so it has to be compressed, but dividing by the busiest spot
  // makes every other area's height a statement about THAT spot: one area three times deeper than the rest drowns
  // all of them under the coastline, which is exactly what a real community looks like and exactly what shipped.
  // Saturation is bounded by construction and says the same thing about an area however busy its neighbour is.
  for (let cell = 0; cell < values.length; cell++) {
    const col = cell % cols;
    const row = (cell - col) / cols;
    const x = col * CELL_PX;
    const y = row * CELL_PX;
    const wander = RIPPLE * ripple(x, y) * borderFade(x, y, size);
    const height = 1 - Math.exp(-values[cell]! / SATURATION) + wander;
    values[cell] = Math.min(1, Math.max(0, height));
  }

  return { cols, rows, cell: CELL_PX, values };
}

/** Bilinear, so the surface reads as ground rather than as stairs; outside the field there is no ground at all. */
export function heightAt(field: Heightfield, x: number, y: number): number {
  const cx = x / field.cell;
  const cy = y / field.cell;
  if (cx < 0 || cy < 0 || cx > field.cols - 1 || cy > field.rows - 1) return 0;

  const col = Math.floor(cx);
  const row = Math.floor(cy);
  const nextCol = Math.min(field.cols - 1, col + 1);
  const nextRow = Math.min(field.rows - 1, row + 1);
  const fx = cx - col;
  const fy = cy - row;

  const topLeft = field.values[row * field.cols + col]!;
  const topRight = field.values[row * field.cols + nextCol]!;
  const bottomLeft = field.values[nextRow * field.cols + col]!;
  const bottomRight = field.values[nextRow * field.cols + nextCol]!;

  const top = topLeft + (topRight - topLeft) * fx;
  const bottom = bottomLeft + (bottomRight - bottomLeft) * fx;
  return top + (bottom - top) * fy;
}
