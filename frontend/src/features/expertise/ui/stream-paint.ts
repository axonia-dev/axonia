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

/** One vertex's paint, as an RGBA attribute holds it: the colour in the renderer's working space, and how solid. */
export type Rgba = readonly [number, number, number, number];

/** What a band, or its edge, is painted in when it is lit and when it is set back. */
export interface BandShades {
  lit: Rgba;
  back: Rgba;
}

const rgbaOf = (colour: THREE.Color, alpha: number): Rgba => [
  colour.r,
  colour.g,
  colour.b,
  alpha,
];

/** Both the band's fill and its edge, resolved out of the theme once, when the band is built. */
export function bandShades(band: Stacked): {
  fill: BandShades;
  edge: BandShades;
} {
  const accent = bandColour(band, true);
  const dim = bandColour(band, false);
  return {
    fill: { lit: rgbaOf(accent, BAND_OPACITY), back: rgbaOf(dim, DIM_OPACITY) },
    edge: { lit: rgbaOf(accent, EDGE_OPACITY), back: rgbaOf(dim, DIM_OPACITY) },
  };
}
