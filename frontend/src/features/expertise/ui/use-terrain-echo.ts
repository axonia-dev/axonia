import { type RefObject, useMemo } from "react";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Tinted } from "@/features/expertise/ui/echo-blend";
import { MAX_DROPS, ringLayer } from "@/features/expertise/ui/echo-rings";
import { type Bounds, ECHO } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { twinkleLayer } from "@/features/expertise/ui/echo-twinkles";
import { MARK_LIFT } from "@/features/expertise/ui/terrain-camera";
import type { Gpu } from "@/features/expertise/ui/terrain-gpu";
import { placeAt } from "@/features/expertise/ui/terrain-ground";
import { standOnGround } from "@/features/expertise/ui/terrain-stencil";
import type { Surface } from "@/features/expertise/ui/terrain-surface";
import {
  type EchoGround,
  useMapEcho,
} from "@/features/expertise/ui/use-map-echo";
import { rendererPixelRatio } from "@/features/expertise/ui/webgl-support";

const RING_LIFT = 1;
/** Over the ground and under every mark, face and halo: a ring passes beneath what it is about. */
const RINGS_ORDER = -0.5;
/** Over the marks a twinkle opens round, and under the one a reader chose. */
const TWINKLES_ORDER = 1.5;

const planeOf = ({ size }: Surface): Bounds => ({
  minX: 0,
  minY: 0,
  maxX: size.width,
  maxY: size.height,
});

function terrainGround(
  surface: Surface,
  marks: readonly PlacedContribution[],
): EchoGround {
  const globe = surface.projection === "globe";
  return {
    bounds: planeOf(surface),
    marks,
    rings: () => {
      const rings = ringLayer({
        place: placeAt(surface, RING_LIFT),
        capacity: ECHO.ringLagsMs.length * MAX_DROPS,
        ...(globe ? { within: planeOf(surface) } : {}),
      });
      // A ring runs past the land, and the flat map's sea beyond the ground is only its clear colour: stood on the
      // ground there, it is cut at a line nobody can see. The globe has a limb, and there the cut is the point.
      if (globe) standOnGround(rings.objects);
      for (const loop of rings.objects) loop.renderOrder = RINGS_ORDER;
      return rings;
    },
    twinkles: ({ points, colour, markPx }) => {
      const made = twinkleLayer({
        points,
        place: placeAt(surface, MARK_LIFT),
        markPx,
        pixelRatio: rendererPixelRatio(),
        colour,
      });
      made.object.renderOrder = TWINKLES_ORDER;
      standOnGround([made.object]);
      return made;
    },
  };
}

/** The terrain's echo: rings laid on the ground itself, and cut at the globe's limb. See `useMapEcho`. */
export function useTerrainEcho({
  gpuRef,
  tintsRef,
  layer,
}: Readonly<{
  gpuRef: RefObject<Gpu | null>;
  tintsRef: RefObject<Set<Tinted>>;
  layer: Readonly<{
    surface: Surface;
    marks: PlacedContribution[];
    drawn: DrawnHighlight;
    markPx: number;
    supported: boolean;
    painted: string;
  }>;
}>): void {
  const { surface, marks, drawn, markPx, supported, painted } = layer;
  const ground = useMemo(() => terrainGround(surface, marks), [surface, marks]);
  useMapEcho({
    gpuRef,
    tintsRef,
    layer: { ground, drawn, markPx, supported, painted },
  });
}
