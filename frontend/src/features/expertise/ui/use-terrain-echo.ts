import { type RefObject, useEffect, useRef } from "react";
import * as THREE from "three";

import { browserClock } from "@/components/patterns/ceremony/clock";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Tinted } from "@/features/expertise/ui/echo-blend";
import { dropsFor } from "@/features/expertise/ui/echo-drops";
import { type EchoStage, play } from "@/features/expertise/ui/echo-play";
import {
  echoInk,
  MAX_DROPS,
  type RingLayer,
  ringLayer,
} from "@/features/expertise/ui/echo-rings";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import {
  type TwinkleLayer,
  type TwinklePoint,
  twinkleLayer,
} from "@/features/expertise/ui/echo-twinkles";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { MARK_LIFT } from "@/features/expertise/ui/terrain-camera";
import { placeAt } from "@/features/expertise/ui/terrain-ground";
import type { Gpu } from "@/features/expertise/ui/terrain-gpu";
import { standOnGround } from "@/features/expertise/ui/terrain-stencil";
import type { Surface } from "@/features/expertise/ui/terrain-surface";
import { rendererPixelRatio } from "@/features/expertise/ui/webgl-support";

const RING_LIFT = 1;
/** Over the ground and under every mark, face and halo: a ring passes beneath what it is about. */
const RINGS_ORDER = -0.5;
/** Over the marks a twinkle opens round, and under the one a reader chose. */
const TWINKLES_ORDER = 1.5;

/** The ring an answer's twinkle is drawn in: the first area's echo, or the map's own ring with no area to take. */
function twinkleInk(drawn: DrawnHighlight, marks: PlacedContribution[]) {
  const first = dropsFor(marks, drawn.highlight.marks ?? new Set())[0];
  return first === undefined
    ? new THREE.Color(groundPalette().ring)
    : echoInk(first.slot);
}

/**
 * Plays every highlight the map is given on the layers it holds: an echo from where the answers are, a fade or a
 * snap. Called after the layers are built, so the colours a highlight moves are already live when it runs. Mounting
 * and rebuilding a layer never echo: what a reader is shown on arrival, or after a theme change, is simply there.
 */
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
  const ringsRef = useRef<RingLayer | null>(null);
  const twinklesRef = useRef<TwinkleLayer | null>(null);
  const stopRef = useRef<() => void>(() => {});
  const playedRef = useRef(drawn);

  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const rings = ringLayer({
      place: placeAt(surface, RING_LIFT),
      capacity: ECHO.ringLagsMs.length * MAX_DROPS,
    });
    standOnGround(rings.objects);
    for (const loop of rings.objects) {
      loop.renderOrder = RINGS_ORDER;
      gpu.scene.add(loop);
    }
    ringsRef.current = rings;
    return () => {
      // The layers this echo was moving are rebuilt in the same commit and painted settled, so it ends with them.
      stopRef.current();
      stopRef.current = () => {};
      ringsRef.current = null;
      for (const loop of rings.objects) loop.removeFromParent();
      rings.dispose();
    };
  }, [gpuRef, surface, supported, painted]);

  useEffect(() => {
    const gpu = gpuRef.current;
    const rings = ringsRef.current;
    if (gpu === null || rings === null) return undefined;

    const again = playedRef.current === drawn;
    playedRef.current = drawn;
    const twinkles = (points: readonly TwinklePoint[]): TwinkleLayer => {
      const made = twinkleLayer({
        points,
        place: placeAt(surface, MARK_LIFT),
        markPx,
        pixelRatio: rendererPixelRatio(),
        colour: twinkleInk(drawn, marks),
      });
      made.object.renderOrder = TWINKLES_ORDER;
      standOnGround([made.object]);
      gpu.scene.add(made.object);
      twinklesRef.current = made;
      return {
        ...made,
        dispose: () => {
          made.object.removeFromParent();
          if (twinklesRef.current === made) twinklesRef.current = null;
          made.dispose();
        },
      };
    };
    const stage: EchoStage = {
      tints: [...tintsRef.current],
      marks,
      bounds: {
        minX: 0,
        minY: 0,
        maxX: surface.size.width,
        maxY: surface.size.height,
      },
      rings,
      twinkles,
      request: gpu.request,
    };
    const stop = play({
      stage,
      drawn: again ? { ...drawn, transition: "snap" } : drawn,
      clock: browserClock,
    });
    stopRef.current = stop;
    return () => {
      stopRef.current = () => {};
      stop();
    };
    // Only a new highlight plays: the layers it plays on are read as they stand when it arrives.
    // oxlint-disable-next-line react/exhaustive-deps
  }, [drawn]);

  useEffect(() => {
    twinklesRef.current?.resize(markPx);
  }, [markPx]);
}
