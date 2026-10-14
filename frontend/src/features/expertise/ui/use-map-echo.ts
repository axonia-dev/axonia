import { type RefObject, useEffect, useRef } from "react";
import * as THREE from "three";

import { browserClock } from "@/components/patterns/ceremony/clock";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { paintNow, type Tinted } from "@/features/expertise/ui/echo-blend";
import { dropsFor } from "@/features/expertise/ui/echo-drops";
import { type EchoStage, play } from "@/features/expertise/ui/echo-play";
import { echoInk, type RingLayer } from "@/features/expertise/ui/echo-rings";
import type { Bounds } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import type {
  TwinkleLayer,
  TwinklePoint,
} from "@/features/expertise/ui/echo-twinkles";
import { groundPalette } from "@/features/expertise/ui/ground-palette";

/** What an echo is drawn into: a scene, and the way to ask for its next frame. */
export interface EchoGpu {
  scene: THREE.Scene;
  request: () => void;
}

/**
 * Where one reading plays an echo: the plane its marks are laid out on, the marks in that plane's own coordinates,
 * and how its rings and twinkles are built for its scene. Each reading places them on its own surface.
 */
export interface EchoGround {
  bounds: Bounds;
  marks: readonly PlacedContribution[];
  rings: () => RingLayer;
  twinkles: (
    made: Readonly<{
      points: readonly TwinklePoint[];
      colour: THREE.Color;
      markPx: number;
    }>,
  ) => TwinkleLayer;
}

/**
 * Takes a freshly built layer's colours into the live set, painted in the highlight that stands now, and gives back
 * what lets them go again when the layer is torn down.
 */
export function holdLive(
  live: Set<Tinted>,
  built: readonly Tinted[],
  drawn: DrawnHighlight,
): () => void {
  for (const tint of built) {
    live.add(tint);
    paintNow(tint, drawn.highlight);
  }
  return () => {
    for (const tint of built) live.delete(tint);
  };
}

/** The ring an answer's twinkle is drawn in: the first area's echo, or the map's own ring with no area to take. */
function twinkleInk(
  drawn: DrawnHighlight,
  marks: readonly PlacedContribution[],
) {
  const first = dropsFor(marks, drawn.highlight.marks ?? new Set())[0];
  return first === undefined
    ? new THREE.Color(groundPalette().ring)
    : echoInk(first.slot);
}

/**
 * Plays every highlight a map is given on the layers it holds: an echo from where the answers are, a fade or a snap.
 * Called after the layers are built, so the colours a highlight moves are already live when it runs. Mounting and
 * rebuilding a layer never echo: what a reader is shown on arrival, or after a theme change, is simply there.
 */
export function useMapEcho({
  gpuRef,
  tintsRef,
  layer,
}: Readonly<{
  gpuRef: RefObject<EchoGpu | null>;
  tintsRef: RefObject<Set<Tinted>>;
  layer: Readonly<{
    ground: EchoGround;
    drawn: DrawnHighlight;
    markPx: number;
    supported: boolean;
    painted: string;
  }>;
}>): void {
  const { ground, drawn, markPx, supported, painted } = layer;
  const ringsRef = useRef<RingLayer | null>(null);
  const twinklesRef = useRef<TwinkleLayer | null>(null);
  const stopRef = useRef<() => void>(() => {});
  const playedRef = useRef(drawn);

  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const rings = ground.rings();
    for (const loop of rings.objects) gpu.scene.add(loop);
    ringsRef.current = rings;
    return () => {
      // The layers this echo was moving are rebuilt in the same commit and painted settled, so it ends with them.
      stopRef.current();
      stopRef.current = () => {};
      ringsRef.current = null;
      for (const loop of rings.objects) loop.removeFromParent();
      rings.dispose();
    };
  }, [gpuRef, ground, supported, painted]);

  useEffect(() => {
    const gpu = gpuRef.current;
    const rings = ringsRef.current;
    if (gpu === null || rings === null) return undefined;

    const again = playedRef.current === drawn;
    playedRef.current = drawn;
    const twinkles = (points: readonly TwinklePoint[]): TwinkleLayer => {
      const made = ground.twinkles({
        points,
        colour: twinkleInk(drawn, ground.marks),
        markPx,
      });
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
      marks: ground.marks,
      bounds: ground.bounds,
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
