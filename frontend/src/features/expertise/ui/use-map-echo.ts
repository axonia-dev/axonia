import { type RefObject, useEffect, useRef } from "react";
import * as THREE from "three";

import { browserClock } from "@/components/patterns/ceremony/clock";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { paintNow, type Tinted } from "@/features/expertise/ui/echo-blend";
import { type EchoStage, play } from "@/features/expertise/ui/echo-play";
import type { RingLayer } from "@/features/expertise/ui/echo-rings";
import type { Bounds } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import type {
  TwinkleLayer,
  TwinklePoint,
} from "@/features/expertise/ui/echo-twinkles";

/** What an echo is drawn into: a scene, and the way to ask for its next frame. */
export interface EchoGpu {
  scene: THREE.Scene;
  request: () => void;
  onScreen: () => boolean;
}

/**
 * Every colour the layers on screen hold, so a highlight repaints them in place rather than rebuilding them, and how
 * to end the echo moving them at once on its final picture.
 */
export interface LiveTints {
  tints: Set<Tinted>;
  settle: () => void;
}

const NOTHING_TO_SETTLE = () => {};

export function liveTints(): LiveTints {
  return { tints: new Set(), settle: NOTHING_TO_SETTLE };
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
    made: Readonly<{ points: readonly TwinklePoint[]; markPx: number }>,
  ) => TwinkleLayer;
}

/**
 * Takes a freshly built layer's colours into the live set, painted in the highlight that stands now, and gives back
 * what lets them go again when the layer is torn down. A layer is built settled, so an echo still moving the others
 * ends with them on the same picture: a mark chosen mid-echo must not leave the marks ahead of the ground.
 */
export function holdLive(
  live: LiveTints,
  built: readonly Tinted[],
  drawn: DrawnHighlight,
): () => void {
  live.settle();
  for (const tint of built) {
    live.tints.add(tint);
    paintNow(tint, drawn.highlight);
  }
  return () => {
    for (const tint of built) live.tints.delete(tint);
  };
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
  tintsRef: RefObject<LiveTints>;
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
  const playedRef = useRef(drawn);

  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const live = tintsRef.current;
    const rings = ground.rings();
    for (const loop of rings.objects) gpu.scene.add(loop);
    ringsRef.current = rings;
    return () => {
      // The layers this echo was moving are rebuilt in the same commit and come back settled: it ends there too.
      live.settle();
      ringsRef.current = null;
      for (const loop of rings.objects) loop.removeFromParent();
      rings.dispose();
    };
  }, [gpuRef, tintsRef, ground, supported, painted]);

  useEffect(() => {
    const gpu = gpuRef.current;
    const rings = ringsRef.current;
    if (gpu === null || rings === null) return undefined;

    const again = playedRef.current === drawn;
    playedRef.current = drawn;
    const twinkles = (points: readonly TwinklePoint[]): TwinkleLayer => {
      const made = ground.twinkles({ points, markPx });
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
    const live = tintsRef.current;
    const stage: EchoStage = {
      tints: [...live.tints],
      marks: ground.marks,
      bounds: ground.bounds,
      rings,
      twinkles,
      request: gpu.request,
      onScreen: gpu.onScreen,
    };
    const stop = play({
      stage,
      drawn: again ? { ...drawn, transition: "snap" } : drawn,
      clock: browserClock,
    });
    live.settle = () => {
      live.settle = NOTHING_TO_SETTLE;
      stop();
      for (const tint of live.tints) paintNow(tint, drawn.highlight);
      gpu.request();
    };
    return () => {
      live.settle = NOTHING_TO_SETTLE;
      stop();
    };
    // Only a new highlight plays: the layers it plays on are read as they stand when it arrives.
    // oxlint-disable-next-line react/exhaustive-deps
  }, [drawn]);

  useEffect(() => {
    twinklesRef.current?.resize(markPx);
  }, [markPx]);
}
