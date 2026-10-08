import { clampZoom } from "@/features/expertise/ui/terrain-aim";
import {
  tanHalfOf,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * A mark's apparent size, in CSS pixels: a share of the box, scaled by the zoom and held between two bounds.
 *
 * A mark is a label on a place rather than a thing standing there, so it is sized in pixels and not in world
 * units. It does grow with the zoom, but nothing like as fast as the ground under it does - it is capped within
 * two clicks of the wheel while the ground goes on spreading to the end of the range. That difference is what
 * makes zooming useful on a crowded area: what separates two superposed marks is the ground between them growing
 * while they do not.
 *
 * The ceiling also keeps a mark a mark. Sized in world units it reached a hundred and sixty pixels at the deep end
 * of the range, which is not a marker any more, it is an illustration.
 */
const MARK_PX_SHARE = 0.0155;
const MARK_PX_MIN = 5;
const MARK_PX_MAX = 22;

export function markPxFor(size: GraphSize, zoom: number): number {
  const base = Math.min(size.width, size.height) * MARK_PX_SHARE;
  return Math.min(MARK_PX_MAX, Math.max(MARK_PX_MIN, base * clampZoom(zoom)));
}

export function markPxAt(view: TerrainView): number {
  return markPxFor(view.size, view.zoom);
}

/** A face answers "who" and has to be told apart from the ground it stands on, so it draws a little over a mark. */
export function facePxAt(view: TerrainView): number {
  return markPxAt(view) * 1.25;
}

/**
 * The `scale` a sprite needs to draw at a given size in CSS pixels, under a material with `sizeAttenuation` off.
 * three.js then multiplies the scale by the view depth, which cancels the perspective divide and leaves the sprite
 * spanning `scale * (height / 2) / tanHalf` pixels of the box however far away it stands.
 */
export function spriteScaleFor(px: number, view: TerrainView): number {
  return (px * 2 * tanHalfOf(view)) / view.size.height;
}
