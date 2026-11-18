import { type ReactNode, useState } from "react";

import { cn } from "@/lib/cn";

import {
  BillboardControls,
  type BillboardControlsTone,
} from "./BillboardControls";
import { useBillboardRotation } from "./use-billboard-rotation";
import { useBillboardTrack } from "./use-billboard-track";

/**
 * A carousel of featured slides: swipe or drag the scroll-snapped track, use the arrows,
 * the dots or the arrow keys, or pause it. It advances on its own between manual moves.
 * Slide content is the caller's; the track, the controls and the rotation are its own.
 */
export function Billboard<T extends { id: string }>({
  label,
  items,
  renderSlide,
  itemLabel,
  controlsTone = "overlay",
  suspended = false,
  initialIndex,
  className,
  controlsClassName,
}: Readonly<{
  label: string;
  items: readonly T[];
  renderSlide: (item: T, active: boolean) => ReactNode;
  /** Names a slide for assistive tech: its group label and its dot. */
  itemLabel?: (item: T) => string;
  /** `overlay` sits on image-backed slides; `surface` on theme panels. */
  controlsTone?: BillboardControlsTone;
  /** Something covers the billboard: hold it still, then give it the post-move grace. */
  suspended?: boolean;
  /** The slide on show when it mounts, e.g. the one a person left from and came back to. */
  initialIndex?: number;
  className?: string;
  /** Repositions the controls, e.g. to mirror the slide copy's inset. */
  controlsClassName?: string;
}>) {
  const count = items.length;
  const rotation = useBillboardRotation(count, { initialIndex });
  const { trackRef, dragging, goTo, sectionProps, trackProps } =
    useBillboardTrack(count, rotation, suspended);
  const [announced, setAnnounced] = useState<number | null>(null);
  const announcement = announced === null ? undefined : items.at(announced);
  const multiple = count > 1;
  const slideLabel = (item: T, i: number) => {
    const position = `${i + 1} of ${count}`;
    return itemLabel === undefined
      ? position
      : `${itemLabel(item)}, ${position}`;
  };

  if (count === 0) return null;

  return (
    <section
      aria-roledescription="carousel"
      aria-label={label}
      className={cn("relative", className)}
      {...sectionProps}
    >
      <div
        ref={trackRef}
        className={cn(
          "relative flex [scrollbar-width:none] overflow-x-auto overscroll-x-contain [&::-webkit-scrollbar]:hidden",
          multiple && "cursor-grab",
          dragging
            ? "cursor-grabbing snap-none select-none"
            : "snap-x snap-mandatory",
        )}
        {...trackProps}
      >
        {items.map((item, i) => (
          <div
            key={item.id}
            role="group"
            aria-roledescription="slide"
            aria-label={slideLabel(item, i)}
            tabIndex={-1}
            inert={i !== rotation.index}
            className="relative w-full flex-none snap-start outline-none"
          >
            {renderSlide(item, i === rotation.index)}
          </div>
        ))}
      </div>
      {multiple && (
        <BillboardControls
          dots={items.map((item, i) => ({
            id: item.id,
            label:
              itemLabel === undefined
                ? `Slide ${i + 1}`
                : `Show ${itemLabel(item)}`,
          }))}
          tone={controlsTone}
          className={controlsClassName}
          rotation={rotation}
          goTo={(target) => setAnnounced(goTo(target))}
        />
      )}
      <p role="status" className="sr-only">
        {announcement === undefined || announced === null
          ? ""
          : slideLabel(announcement, announced)}
      </p>
    </section>
  );
}
