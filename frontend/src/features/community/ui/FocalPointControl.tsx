import { useRef } from "react";

import { CENTRE, type FocalPoint } from "@/features/community/domain/community";
import {
  useFocalDrag,
  useFocalSliders,
} from "@/features/community/ui/use-focal-point";
import { cn } from "@/lib/cn";

interface FocalPointControlProps {
  /** Where the image sits now. */
  value: FocalPoint | null | undefined;
  isDisabled: boolean;
  /**
   * Commits a new position. May return a promise (e.g. `mutateAsync`) - a rejection restores the
   * preview, since a failed commit leaves the stored value unchanged but a direct DOM write would
   * otherwise sit there un-reconciled.
   */
  onCommit: (focus: FocalPoint) => void | Promise<unknown>;
}

function cursorClass(isDisabled: boolean, dragging: boolean): string {
  if (isDisabled) return "cursor-default";
  return dragging ? "cursor-grabbing" : "cursor-grab";
}

/**
 * Drag the image inside its frame to choose which part of it survives the crop.
 *
 * Must be placed inside a `[data-focal-frame]` element containing an `img[data-focal-image]` - it
 * resolves both from the DOM (see `use-focal-point.ts`) rather than through ref props, so it can
 * be dropped into the hero card as a bare overlay.
 *
 * Commits on release, not on every move: a drag is one decision, one mutation, and a press that
 * never moved commits nothing. The live preview writes `objectPosition` straight onto the image's
 * `style`, bypassing React state, because a drag fires pointer events at screen rate.
 *
 * The surface itself carries only pointer handlers, no `role` or keyboard handling: two sr-only
 * range inputs give keyboard and screen-reader access instead, since a native range input
 * announces its value and gets key-repeat for free, which a keydown handler does not.
 */
export function FocalPointControl({
  value,
  isDisabled,
  onCommit,
}: Readonly<FocalPointControlProps>) {
  const rootRef = useRef<HTMLDivElement>(null);
  const point = value ?? CENTRE;
  const drag = useFocalDrag({ point, isDisabled, rootRef, onCommit });
  const sliders = useFocalSliders({ point, isDisabled, rootRef, onCommit });

  return (
    <div
      ref={rootRef}
      data-testid="focal-surface"
      aria-disabled={isDisabled}
      className={cn(
        "absolute inset-0 touch-none",
        "has-[:focus-visible]:ring-primary has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-inset",
        cursorClass(isDisabled, drag.dragging),
      )}
      onPointerDown={drag.onPointerDown}
      onPointerMove={drag.onPointerMove}
      onPointerUp={drag.onPointerUp}
      onPointerCancel={drag.onPointerCancel}
    >
      <input
        type="range"
        className="sr-only"
        min={0}
        max={100}
        disabled={isDisabled}
        value={sliders.draft.x}
        aria-label="Horizontal position"
        onChange={(e) => sliders.onHorizontalChange(Number(e.target.value))}
      />
      <input
        type="range"
        className="sr-only"
        min={0}
        max={100}
        disabled={isDisabled}
        value={sliders.draft.y}
        aria-label="Vertical position"
        onChange={(e) => sliders.onVerticalChange(Number(e.target.value))}
      />
      <span
        aria-hidden
        className={cn(
          "pointer-events-none absolute inset-0 transition-opacity duration-150",
          drag.dragging ? "opacity-100" : "opacity-0",
        )}
      >
        <span className="absolute inset-y-0 left-1/3 w-1/3 border-x border-white/40" />
        <span className="absolute inset-x-0 top-1/3 h-1/3 border-y border-white/40" />
      </span>
      <span
        className={cn(
          "pointer-events-none absolute inset-x-0 bottom-0 bg-linear-to-t from-black/60 to-transparent p-1.5",
          "text-center text-xs font-semibold text-white opacity-0 transition-opacity duration-150",
          !isDisabled &&
            "group-hover/badge:opacity-100 group-hover/banner:opacity-100",
        )}
      >
        Drag to reposition
      </span>
    </div>
  );
}
