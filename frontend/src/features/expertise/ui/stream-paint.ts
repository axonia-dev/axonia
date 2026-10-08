/**
 * What the stream is painted in: how solid a band is, and what colour it takes.
 */
import * as THREE from "three";

import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { accentHexOf } from "@/features/expertise/ui/group-accents";
import type { Stacked } from "@/features/expertise/ui/stream-layout";
import { dimAccent } from "@/features/expertise/ui/terrain-shading";

export const BAND_OPACITY = 0.52;
export const EDGE_OPACITY = 0.85;

/** A band a reading is not about keeps its shape - the history is still the history - and gives up its weight. */
export const DIM_OPACITY = 0.3;

export function bandColour(band: Stacked, lit: boolean): THREE.Color {
  const hex = accentHexOf(band.slot);
  if (lit) return new THREE.Color(hex);
  const { r, g, b } = dimAccent(hex, groundPalette());
  return new THREE.Color().setRGB(r, g, b, THREE.SRGBColorSpace);
}
