import type { PointerEvent } from "react";
import { useEffect, useLayoutEffect, useRef } from "react";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import { atFraction, nearestTick } from "@/features/events/domain/ribbon";

const msOf = (tick: RibbonTick | null) =>
  tick ? new Date(tick.at).getTime() : null;

const DAY_MS = 86_400_000;
/** How wide each edge of the track is where a held pointer rolls the window. */
const EDGE_PX = 32;
/** How fast the window rolls under a pointer pressed right against an edge. */
const SLIDE_MS_PER_SECOND = 120 * DAY_MS;
/** A dropped frame never rolls the window by more than this much time on screen. */
const MAX_FRAME_MS = 50;

/** -1 to 1: how hard a pointer presses into the left or right edge, 0 between them. */
function edgePull(clientX: number, rect: DOMRect): number {
  const intoRight = (clientX - (rect.right - EDGE_PX)) / EDGE_PX;
  const intoLeft = (rect.left + EDGE_PX - clientX) / EDGE_PX;
  if (intoRight > 0) return Math.min(1, intoRight);
  if (intoLeft > 0) return -Math.min(1, intoLeft);
  return 0;
}

const slideSpeed = (pull: number) =>
  Math.sign(pull) * (0.2 + 0.8 * Math.abs(pull)) * SLIDE_MS_PER_SECOND;

interface Scrubbing {
  span: RibbonSpan;
  ticks: RibbonTick[];
  onScrub: (atMs: number) => void;
  onSlide: (deltaMs: number) => void;
  onHold: (held: boolean) => void;
}

/**
 * Scrubs to the session nearest a held pointer. Held on either edge, the pointer rolls the window
 * on every frame and keeps scrubbing to whatever session rolls in under it, until it moves off
 * the edge or lets go.
 */
export function useScrubbing(props: Scrubbing) {
  const latest = useRef(props);
  const held = useRef<{ track: HTMLElement; clientX: number } | null>(null);
  const last = useRef<number | null>(null);
  const frame = useRef<number | null>(null);

  useLayoutEffect(() => {
    latest.current = props;
  });

  function scrubAt(track: HTMLElement, clientX: number) {
    const rect = track.getBoundingClientRect();
    if (rect.width <= 0) return;
    const { span, ticks, onScrub } = latest.current;
    const fraction = (clientX - rect.left) / rect.width;
    const at = msOf(nearestTick(ticks, atFraction(fraction, span)));
    if (at === null || at === last.current) return;
    last.current = at;
    onScrub(at);
  }

  function stopRolling() {
    if (frame.current !== null) cancelAnimationFrame(frame.current);
    frame.current = null;
  }

  function roll(previous: number) {
    frame.current = requestAnimationFrame((time) => {
      const pointer = held.current;
      const pull =
        pointer === null || !pointer.track.isConnected
          ? 0
          : edgePull(pointer.clientX, pointer.track.getBoundingClientRect());
      if (pointer === null || pull === 0) {
        frame.current = null;
        return;
      }
      scrubAt(pointer.track, pointer.clientX);
      const elapsed = Math.min(time - previous, MAX_FRAME_MS);
      latest.current.onSlide((slideSpeed(pull) * Math.max(0, elapsed)) / 1000);
      roll(time);
    });
  }

  function track(event: PointerEvent<HTMLDivElement>) {
    const pointer = {
      track: event.currentTarget,
      clientX: event.clientX,
    };
    held.current = pointer;
    scrubAt(pointer.track, pointer.clientX);
    const pull = edgePull(
      pointer.clientX,
      pointer.track.getBoundingClientRect(),
    );
    if (pull !== 0 && frame.current === null) roll(performance.now());
  }

  function release() {
    if (held.current === null) return;
    held.current = null;
    stopRolling();
    latest.current.onHold(false);
  }

  // A ribbon taken away under a held pointer never hears it let go.
  useEffect(() => release, []);

  return {
    onPointerDown: (event: PointerEvent<HTMLDivElement>) => {
      last.current = null;
      event.currentTarget.setPointerCapture(event.pointerId);
      latest.current.onHold(true);
      track(event);
    },
    onPointerMove: (event: PointerEvent<HTMLDivElement>) => {
      if (held.current !== null) track(event);
    },
    onPointerUp: (event: PointerEvent<HTMLDivElement>) => {
      event.currentTarget.releasePointerCapture(event.pointerId);
      release();
    },
    onPointerCancel: release,
    onLostPointerCapture: release,
  };
}
