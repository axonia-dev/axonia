import * as THREE from "three";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Tinted } from "@/features/expertise/ui/echo-blend";
import {
  type MapHighlight,
  markLit,
} from "@/features/expertise/ui/map-highlight";
import type { Rgb } from "@/features/expertise/ui/terrain-shading";

/** One colour in the renderer's working space, as an attribute holds it. */
export type Linear = readonly [number, number, number];

/** The atlas and the ink are written in sRGB, so the conversion into the renderer's working space is stated here. */
export function linearOf({ r, g, b }: Rgb): Linear {
  const colour = new THREE.Color().setRGB(r, g, b, THREE.SRGBColorSpace);
  return [colour.r, colour.g, colour.b];
}

/** What one pass of a mark is drawn in: full strength, and set back for a mark the map is not about. */
export interface Shades {
  full: Linear;
  back: Linear;
}

/** Where each mark sits on the plane it was laid out on, which is what a transition's front sweeps across. */
export function layoutXy(marks: readonly PlacedContribution[]): Float32Array {
  const xy = new Float32Array(marks.length * 2);
  marks.forEach((mark, index) => xy.set([mark.x, mark.y], index * 2));
  return xy;
}

/**
 * One pass of one tile's marks as a thing a highlight repaints in place. The glyph and the rim share their positions
 * but not their colours, so each takes a tint of its own.
 */
export function markTint({
  marks,
  xy,
  colour,
  shades,
}: Readonly<{
  marks: readonly PlacedContribution[];
  xy: Float32Array;
  colour: THREE.BufferAttribute;
  shades: Shades;
}>): Tinted {
  return {
    xy,
    stride: 3,
    values: colour.array as Float32Array,
    targetFor: (highlight: MapHighlight) => {
      const target = new Float32Array(marks.length * 3);
      marks.forEach((mark, index) =>
        target.set(
          markLit(highlight, mark) ? shades.full : shades.back,
          index * 3,
        ),
      );
      return target;
    },
    commit: () => {
      colour.needsUpdate = true;
    },
  };
}
