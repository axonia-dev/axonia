import { useEffect, useRef, useState } from "react";

import type { FocalPoint } from "@/features/community/domain/community";

interface DragOrigin {
  x: number;
  y: number;
  point: FocalPoint;
}

const COMMIT_DELAY_MS = 500;

function clamp(value: number): number {
  return Math.min(100, Math.max(0, Math.round(value)));
}

/**
 * How much of the image is hidden outside its own rendered box. `object-fit: cover` fits the
 * natural image into the `<img>`'s own content box, not the frame around it - a bordered image
 * (the badge) must be measured on itself, or the border undercounts the overflow. Zero on an axis
 * means that axis is already fully visible and cannot move.
 */
function overflow(image: HTMLImageElement | null): { x: number; y: number } {
  if (image === null) {
    return { x: 0, y: 0 };
  }
  const {
    clientWidth: iw,
    clientHeight: ih,
    naturalWidth: nw,
    naturalHeight: nh,
  } = image;
  if (nw === 0 || nh === 0) {
    return { x: 0, y: 0 };
  }
  const scale = Math.max(iw / nw, ih / nh);
  return { x: nw * scale - iw, y: nh * scale - ih };
}

/**
 * Resolves the image this control reads and previews against. Composed as an overlay two levels
 * above `HeroBanner`/`HeroIdentity`'s own frame and `<img>`, so it looks them up from the DOM
 * rather than through ref props. The frame is only the lookup anchor; the image is what gets
 * measured (see `overflow`).
 */
function resolveImage(root: HTMLElement | null): HTMLImageElement | null {
  const frame = root?.closest<HTMLElement>("[data-focal-frame]") ?? null;
  return (
    frame?.querySelector<HTMLImageElement>("img[data-focal-image]") ?? null
  );
}

function writePreview(root: HTMLElement | null, next: FocalPoint): void {
  const image = resolveImage(root);
  if (image !== null) {
    image.style.objectPosition = `${next.x}% ${next.y}%`;
  }
}

interface CommitArgs {
  point: FocalPoint;
  rootRef: React.RefObject<HTMLDivElement | null>;
  onCommit: (focus: FocalPoint) => void | Promise<unknown>;
}

/**
 * Commits a point; a rejection restores the preview to the last known-good value. Shared by the
 * drag surface and the sliders, since both write a live DOM preview ahead of the mutation.
 *
 * `onRevert` lets a caller that keeps its own in-flight state (the sliders' `draft`) fold that
 * state back to `point` in the same place the DOM preview is reverted, rather than as a second
 * path that has to independently agree with this one. The drag surface has no such state and
 * passes nothing.
 */
function useFocalCommit({ point, rootRef, onCommit }: CommitArgs) {
  function commit(next: FocalPoint, onRevert?: () => void) {
    const result = onCommit(next);
    if (result instanceof Promise) {
      result.catch(() => {
        writePreview(rootRef.current, point);
        onRevert?.();
      });
    }
  }

  return { commit };
}

export function useFocalDrag({
  point,
  isDisabled,
  rootRef,
  onCommit,
}: CommitArgs & { isDisabled: boolean }) {
  const { commit } = useFocalCommit({ point, rootRef, onCommit });
  const start = useRef<DragOrigin | null>(null);
  const latest = useRef<FocalPoint>(point);
  const [dragging, setDragging] = useState(false);

  function handleMove(clientX: number, clientY: number) {
    const from = start.current;
    if (from === null) return;
    const image = resolveImage(rootRef.current);
    const room = overflow(image);
    const next = {
      x:
        room.x === 0
          ? from.point.x
          : clamp(from.point.x - ((clientX - from.x) / room.x) * 100),
      y:
        room.y === 0
          ? from.point.y
          : clamp(from.point.y - ((clientY - from.y) / room.y) * 100),
    };
    latest.current = next;
    if (image !== null) {
      image.style.objectPosition = `${next.x}% ${next.y}%`;
    }
  }

  function onPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    if (isDisabled) return;
    start.current = { x: event.clientX, y: event.clientY, point };
    latest.current = point;
    setDragging(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function onPointerMove(event: React.PointerEvent<HTMLDivElement>) {
    handleMove(event.clientX, event.clientY);
  }

  function onPointerUp() {
    const from = start.current;
    start.current = null;
    setDragging(false);
    if (from === null) return;
    const next = latest.current;
    if (next.x !== from.point.x || next.y !== from.point.y) {
      commit(next);
    }
  }

  function onPointerCancel() {
    start.current = null;
    setDragging(false);
    writePreview(rootRef.current, point);
  }

  return {
    dragging,
    onPointerDown,
    onPointerMove,
    onPointerUp,
    onPointerCancel,
  };
}

/**
 * The keyboard path: two range inputs, one per axis. A slider's "larger value = further along the
 * axis" is exactly what the stored percentage means, and what the drag above computes into
 * `next.x`/`next.y` - no inversion, unlike the arrow-key nudge this replaced.
 *
 * The in-flight value lives in local state, adjusted from `point` during render (React's
 * documented pattern for resetting derived state, rather than an effect) rather than re-read from
 * `point` on every change, so a burst of changes accumulates instead of each one recomputing from
 * the same not-yet-committed prop. The commit is debounced so a burst becomes one mutation.
 */
export function useFocalSliders({
  point,
  isDisabled,
  rootRef,
  onCommit,
}: CommitArgs & { isDisabled: boolean }) {
  const { commit } = useFocalCommit({ point, rootRef, onCommit });
  const [draft, setDraft] = useState<FocalPoint>(point);
  const [seen, setSeen] = useState<FocalPoint>(point);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  if (seen.x !== point.x || seen.y !== point.y) {
    setSeen(point);
    setDraft(point);
  }

  useEffect(() => {
    return () => {
      if (timer.current !== null) clearTimeout(timer.current);
    };
  }, []);

  // A control that goes disabled mid-debounce (e.g. another mutation started) should not fire a
  // queued commit later on a surface the Organizer can no longer interact with.
  //
  // This only cancels the pending commit; it deliberately leaves `draft` and the imperative
  // `objectPosition` write from the last `change()` showing the uncommitted value. React will not
  // reclaim that direct DOM write on this re-render, because the `style` prop it wrote through is
  // unchanged from React's point of view - a plain mutation on the node, not a prop React diffs -
  // so the stale preview persists on screen rather than self-healing back to `point`.
  useEffect(() => {
    if (isDisabled && timer.current !== null) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  }, [isDisabled]);

  function change(next: FocalPoint) {
    if (isDisabled) return;
    setDraft(next);
    writePreview(rootRef.current, next);
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      timer.current = null;
      commit(next, () => {
        setSeen(point);
        setDraft(point);
      });
    }, COMMIT_DELAY_MS);
  }

  return {
    draft,
    onHorizontalChange: (x: number) => change({ x: clamp(x), y: draft.y }),
    onVerticalChange: (y: number) => change({ x: draft.x, y: clamp(y) }),
  };
}
