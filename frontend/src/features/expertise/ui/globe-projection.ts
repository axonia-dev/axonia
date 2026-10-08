import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

export type Projection = "globe" | "flat";

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/**
 * Latitude is compressed to this fraction of a hemisphere. At the full pole an equirectangular wrap crushes an entire
 * row of the plane into one point, and every mark in it lands on top of every other.
 */
export const LATITUDE_SPAN = 0.8;

/**
 * The same plane the flat reading draws, wrapped onto a sphere. Flat and globe are one layout under two projections,
 * so a mark keeps its identity, its colour, its neighbours and its height across the toggle.
 */
export function planeToSphere(
  x: number,
  y: number,
  size: GraphSize,
  radius: number,
): Vec3 {
  // Measured from the middle of the plane, not from its left edge, so the middle of the plane faces the reader.
  // The force layout keeps every area near the centre, and an edge-anchored wrap puts that centre on the far
  // side of the globe: the reader is shown the one hemisphere with nothing on it.
  const longitude = (x / size.width - 0.5) * Math.PI * 2;
  const latitude = (0.5 - y / size.height) * Math.PI * LATITUDE_SPAN;
  const ring = Math.cos(latitude) * radius;

  return {
    x: Math.sin(longitude) * ring,
    y: Math.sin(latitude) * radius,
    z: Math.cos(longitude) * ring,
  };
}
