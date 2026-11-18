import {
  type FocusEvent,
  type HTMLAttributes,
  type KeyboardEvent,
  type PointerEvent,
  type RefObject,
  type SyntheticEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type WheelEvent,
} from "react";

import { useMediaQuery } from "@/lib/use-media-query";

import type { BillboardRotation } from "./use-billboard-rotation";

const DRAG_THRESHOLD_PX = 60;
const DRAG_CLICK_SLOP_PX = 4;

type Drag = { x: number; left: number; moved: boolean };

export type BillboardTrack = {
  trackRef: RefObject<HTMLDivElement | null>;
  dragging: boolean;
  goTo: (target: number) => number;
  sectionProps: HTMLAttributes<HTMLElement>;
  trackProps: HTMLAttributes<HTMLDivElement>;
};

function slideAt(track: HTMLElement): number {
  return Math.round(track.scrollLeft / track.clientWidth);
}

function dragShift(dx: number): number {
  if (dx < -DRAG_THRESHOLD_PX) return 1;
  if (dx > DRAG_THRESHOLD_PX) return -1;
  return 0;
}

function keyStep(key: string): number {
  if (key === "ArrowRight") return 1;
  if (key === "ArrowLeft") return -1;
  return 0;
}

function isWithin(target: EventTarget, selector: string): boolean {
  return target instanceof Element && target.closest(selector) !== null;
}

/**
 * Binds the scroll-snapped track to the rotation: the scroll position picks the active
 * slide, an index change scrolls to it, and swipe, mouse drag, wheel and arrow keys count
 * as manual moves. Hover and keyboard focus hold the rotation.
 */
export function useBillboardTrack(
  count: number,
  rotation: BillboardRotation,
  suspended: boolean,
): BillboardTrack {
  const { index, select, nudge, hold, release } = rotation;
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const trackRef = useRef<HTMLDivElement>(null);
  const pendingTarget = useRef<number | null>(null);
  const drag = useRef<Drag | null>(null);
  const suppressClick = useRef(false);
  const pointerFocus = useRef(false);
  const focusSlideOnMove = useRef(false);
  const [dragging, setDragging] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const multiple = count > 1;

  useEffect(() => {
    if (hovered || focused || suspended) hold();
    else release();
  }, [hovered, focused, suspended, hold, release]);

  const wasSuspended = useRef(suspended);
  useEffect(() => {
    if (wasSuspended.current && !suspended) nudge();
    wasSuspended.current = suspended;
  }, [suspended, nudge]);

  const scrollToSlide = useCallback(
    (target: number) => {
      const track = trackRef.current;
      if (track === null || track.clientWidth === 0) return;
      const left = target * track.clientWidth;
      if (Math.abs(track.scrollLeft - left) < 1) return;
      pendingTarget.current = target;
      track.scrollTo({ left, behavior: reducedMotion ? "auto" : "smooth" });
    },
    [reducedMotion],
  );

  // Placed before the first paint, so a billboard opening on a later slide never shows the first.
  const placed = useRef(false);
  useLayoutEffect(() => {
    const track = trackRef.current;
    if (placed.current || track === null || track.clientWidth === 0) return;
    placed.current = true;
    track.scrollLeft = index * track.clientWidth;
  }, [index]);

  useEffect(() => {
    const track = trackRef.current;
    if (track === null || track.clientWidth === 0 || drag.current !== null)
      return;
    if (slideAt(track) !== index) scrollToSlide(index);
    if (focusSlideOnMove.current) {
      focusSlideOnMove.current = false;
      const slide = track.children.item(index);
      if (slide instanceof HTMLElement) slide.focus({ preventScroll: true });
    }
  }, [index, scrollToSlide]);

  const goTo = (target: number): number => {
    const clamped = Math.max(0, Math.min(count - 1, target));
    nudge();
    select(clamped);
    scrollToSlide(clamped);
    return clamped;
  };

  const takeOver = () => {
    pendingTarget.current = null;
    nudge();
  };

  const onScroll = (event: SyntheticEvent<HTMLDivElement>) => {
    const track = event.currentTarget;
    if (track.clientWidth === 0) return;
    const position = slideAt(track);
    if (pendingTarget.current !== null) {
      if (position === pendingTarget.current) pendingTarget.current = null;
      return;
    }
    if (position !== index && position >= 0 && position < count)
      select(position);
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (!multiple || event.pointerType !== "mouse" || event.button !== 0)
      return;
    if (isWithin(event.target, "a, button, input, textarea")) return;
    const track = event.currentTarget;
    drag.current = { x: event.clientX, left: track.scrollLeft, moved: false };
    pendingTarget.current = null;
    track.setPointerCapture(event.pointerId);
    setDragging(true);
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const current = drag.current;
    if (current === null) return;
    const dx = event.clientX - current.x;
    if (Math.abs(dx) > DRAG_CLICK_SLOP_PX) current.moved = true;
    event.currentTarget.scrollLeft = current.left - dx;
  };

  const endDrag = (event: PointerEvent<HTMLDivElement>) => {
    const current = drag.current;
    if (current === null) return;
    drag.current = null;
    setDragging(false);
    suppressClick.current = current.moved;
    if (!current.moved) return;
    const width = event.currentTarget.clientWidth;
    const start = width === 0 ? index : Math.round(current.left / width);
    goTo(start + dragShift(event.clientX - current.x));
  };

  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (!multiple || event.altKey || event.ctrlKey || event.metaKey) return;
    if (isWithin(event.target, "input, textarea, select")) return;
    const step = keyStep(event.key);
    if (step === 0) return;
    event.preventDefault();
    focusSlideOnMove.current =
      event.target instanceof Node &&
      (trackRef.current?.contains(event.target) ?? false);
    goTo(index + step);
  };

  return {
    trackRef,
    dragging,
    goTo,
    sectionProps: {
      onPointerEnter: (event: PointerEvent<HTMLElement>) =>
        setHovered(event.pointerType === "mouse"),
      onPointerLeave: () => setHovered(false),
      onPointerDownCapture: () => {
        pointerFocus.current = true;
      },
      onPointerUpCapture: () => {
        pointerFocus.current = false;
      },
      onPointerCancelCapture: () => {
        pointerFocus.current = false;
      },
      onFocus: () => {
        if (!pointerFocus.current) setFocused(true);
      },
      onBlur: (event: FocusEvent<HTMLElement>) => {
        if (!event.currentTarget.contains(event.relatedTarget))
          setFocused(false);
      },
      onKeyDown,
    },
    trackProps: {
      onScroll,
      onScrollEnd: () => {
        pendingTarget.current = null;
      },
      onWheel: (event: WheelEvent<HTMLDivElement>) => {
        if (Math.abs(event.deltaX) > Math.abs(event.deltaY)) takeOver();
      },
      onTouchStart: multiple ? takeOver : undefined,
      onPointerDown,
      onPointerMove,
      onPointerUp: endDrag,
      onPointerCancel: endDrag,
      onClickCapture: (event: SyntheticEvent) => {
        if (!suppressClick.current) return;
        suppressClick.current = false;
        event.preventDefault();
        event.stopPropagation();
      },
    },
  };
}
