import { type PointerEvent, useRef, useState } from "react";

import type { Projection } from "@/features/expertise/ui/globe-projection";
import {
  clampPan,
  clampSpin,
  type Pan,
  RESTING_PAN,
  restingSpin,
  type Spin,
} from "@/features/expertise/ui/terrain-aim";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/** How far the camera turns per pixel dragged. A little under three screen widths takes the globe once round. */
const DEGREES_PER_PX = 0.28;

/**
 * Under this a press that moved is a click whose hand wandered, not a drag, and the mark under it must still open.
 * Above it the press has moved the picture and opens nothing.
 */
const DRAG_SLOP = 4;

/**
 * What a drag does to the picture.
 *
 * The globe hides a hemisphere by construction, so a drag turns it: the far side is not a limitation to be
 * explained, it is somewhere to go and look. The flat reading and the stream have no far side and instead have more
 * of themselves than the frame holds once a reader has zoomed in, so a drag carries the frame across them. Nothing
 * else is a sensible reading of the same gesture on either.
 */
export type NavigationMode = "turn" | "pan";

interface NavigationHandlers {
  onPointerDown: (event: PointerEvent<HTMLElement>) => void;
  onPointerMove: (event: PointerEvent<HTMLElement>) => void;
  onPointerUp: (event: PointerEvent<HTMLElement>) => void;
  onPointerCancel: (event: PointerEvent<HTMLElement>) => void;
}

interface Point {
  x: number;
  y: number;
}

const pointOf = (event: PointerEvent<HTMLElement>): Point => ({
  x: event.clientX,
  y: event.clientY,
});

/** How far apart two fingers are, or null unless exactly two are down: a third finger is not a pinch of anything. */
function spreadOf(pointers: ReadonlyMap<number, Point>): number | null {
  if (pointers.size !== 2) return null;
  const [a, b] = [...pointers.values()] as [Point, Point];
  return Math.hypot(a.x - b.x, a.y - b.y);
}

/**
 * Whether a finger on the drawing is a finger on the picture or on the page. The globe always has a far side to turn
 * to. The flat reading and the stream have somewhere to go only once zoomed in: at rest a drag moves nothing, and a
 * touch the picture cannot use is handed back to the page so a phone can still scroll past it. A pinch is never the
 * page's either way, so the page takes a vertical swipe and nothing else.
 */
export function holdsTheTouch(mode: NavigationMode, zoom: number): boolean {
  return mode === "turn" || zoom > 1;
}

export interface Navigation {
  spin: Spin;
  pan: Pan;
  /** True while a drag that has actually moved the picture is in flight, and until the press that made it ends. */
  moved: () => boolean;
  /** Called by whatever changes the reading, taking where it is going: see {@link Navigation.reset}. */
  reset: (to: Projection) => void;
  handlers: NavigationHandlers;
}

/**
 * Moving the picture with the pointer.
 *
 * `reset` is called by whatever changes the reading, not by an effect watching the projection: flat and globe are
 * one layout under two cameras, and a reader coming back to the globe should find it where they left the picture
 * rather than where a drag on a different reading happened to leave a number. It takes where it is going, because
 * the projection this was rendered with is still the old one at the moment the reading changes.
 *
 * `step` is how much of the picture one pixel of the box covers, which the camera answers and this hook does not
 * try to work out: it depends on the projection, the box and the zoom all at once.
 */
export function useTerrainNavigation(
  at: Readonly<{
    mode: NavigationMode;
    projection: Projection;
    size: GraphSize;
    zoom: number;
    zoomBy: (factor: number) => void;
    step: Pan;
  }>,
): Navigation {
  const { mode, projection, size, zoom, zoomBy, step } = at;
  const [spin, setSpin] = useState<Spin>(() => restingSpin(projection));
  const [pan, setPan] = useState<Pan>(RESTING_PAN);
  // Every pointer that is down, by id. Tracked apart because a second finger's events arrive interleaved with the
  // first's, and a drag measured from one finger to the other throws the picture the width of the gap between them.
  const pointers = useRef(new Map<number, Point>());
  const from = useRef<Point | null>(null);
  const spread = useRef<number | null>(null);
  const moved = useRef(false);

  function release(event: PointerEvent<HTMLElement>) {
    pointers.current.delete(event.pointerId);
    spread.current = spreadOf(pointers.current);
    if (event.currentTarget.hasPointerCapture?.(event.pointerId) === true) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    const [left] = [...pointers.current.values()];
    if (left !== undefined) {
      // The finger left behind after a pinch carries on from where it is, not from where it first pressed. The
      // gesture it belongs to has already moved the picture, so lifting it opens nothing.
      from.current = left;
      return;
    }
    from.current = null;
    // Cleared here rather than on the next press, so that the hover feedback comes back the moment a drag ends
    // instead of staying dead until the reader presses again. Whoever needs to know a press moved the picture
    // reads it before handing the event on.
    moved.current = false;
  }

  function pinch() {
    const before = spread.current;
    const now = spreadOf(pointers.current);
    spread.current = now;
    if (before === null || now === null || before === 0) return;
    zoomBy(now / before);
  }

  function drag(to: Point) {
    const start = from.current;
    if (start === null) return;
    const dx = to.x - start.x;
    const dy = to.y - start.y;
    if (!moved.current && Math.hypot(dx, dy) < DRAG_SLOP) return;

    moved.current = true;
    from.current = to;

    // The surface follows the hand, whichever thing the hand is moving: dragging right turns the globe right,
    // which moves the eye the other way, and dragging right on a map carries the frame left over it.
    if (mode === "turn") {
      // Divided by the zoom, because a narrower lens means the same turn sweeps a wider arc across the frame.
      // Undivided, a drag at the deep end of the range throws the reader clear across the globe.
      const perPixel = DEGREES_PER_PX / zoom;
      setSpin((now) =>
        clampSpin({
          yaw: now.yaw - dx * perPixel,
          pitch: now.pitch + dy * perPixel,
        }),
      );
    } else {
      setPan((now) =>
        clampPan(
          { x: now.x - dx * step.x, y: now.y - dy * step.y },
          size,
          zoom,
        ),
      );
    }
  }

  return {
    spin,
    pan,
    moved: () => moved.current,

    reset(to) {
      setSpin(restingSpin(to));
      setPan(RESTING_PAN);
      // A finger still down when the reading changes belongs to a gesture already under way, so its lift must not
      // land as a tap on whatever mark the new reading has put under it.
      moved.current = pointers.current.size > 0;
      pointers.current.clear();
      from.current = null;
      spread.current = null;
    },

    handlers: {
      onPointerDown(event) {
        // The primary button only: a right or middle press belongs to the browser, and a secondary press that
        // moved the picture would leave the context menu open over something that had shifted under it.
        if (event.button !== 0) return;
        pointers.current.set(event.pointerId, pointOf(event));
        // Captured, so a hand that leaves the drawing mid-drag keeps moving it instead of the picture stopping at
        // the edge of its own box.
        event.currentTarget.setPointerCapture?.(event.pointerId);
        if (pointers.current.size === 1) {
          from.current = pointOf(event);
          moved.current = false;
          return;
        }
        // A second finger makes it a pinch, which is never a click on whatever ends up under either finger.
        from.current = null;
        moved.current = true;
        spread.current = spreadOf(pointers.current);
      },

      onPointerMove(event) {
        if (!pointers.current.has(event.pointerId)) return;
        pointers.current.set(event.pointerId, pointOf(event));
        if (pointers.current.size > 1) pinch();
        else drag(pointOf(event));
      },

      onPointerUp: release,
      onPointerCancel: release,
    },
  };
}
