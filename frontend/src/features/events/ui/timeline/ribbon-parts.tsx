import type { CSSProperties, ReactNode } from "react";

import type { RibbonSpan } from "@/features/events/domain/ribbon";
import { fractionOf, monthMarks } from "@/features/events/domain/ribbon";
import { cn } from "@/lib/cn";

export const percent = (fraction: number) => `${fraction * 100}%`;

/** Centred under its month, sliding to left or right alignment toward the edges so it is never cut. */
const labelPlace = (fraction: number): CSSProperties => ({
  left: percent(fraction),
  transform: `translateX(-${percent(fraction)})`,
});

/** The topbar's surface, bled across `<main>` and faded out below; ink overflow, so it never scrolls. */
const COVER_FADE_PX = 14;
const COVER: CSSProperties = {
  borderImage: `linear-gradient(to bottom, var(--background) calc(100% - ${COVER_FADE_PX}px), transparent) fill 0 / 0 / 0 100vmax ${COVER_FADE_PX}px`,
};

export function Cover({ shown }: Readonly<{ shown: boolean }>) {
  return (
    <span
      aria-hidden="true"
      data-ribbon-cover=""
      style={COVER}
      className={cn(
        "pointer-events-none absolute inset-0 -z-10",
        shown ? "opacity-100" : "opacity-0 motion-safe:transition-opacity",
      )}
    />
  );
}

export function StepButton({
  onPress,
  children,
}: Readonly<{ onPress: () => void; children: ReactNode }>) {
  return (
    <button
      type="button"
      onClick={onPress}
      className="border-border-strong text-primary-strong hover:border-primary hover:bg-primary/5 inline-flex shrink-0 cursor-pointer items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold whitespace-nowrap transition-colors max-sm:size-9 max-sm:justify-center max-sm:px-0"
    >
      {children}
    </button>
  );
}

export function MonthLabels({
  span,
  nowMs,
}: Readonly<{ span: RibbonSpan; nowMs: number }>) {
  return (
    <div
      aria-hidden="true"
      data-ribbon-months=""
      className="relative mt-1 h-3.5 overflow-hidden text-[9.5px] font-bold tracking-widest uppercase"
    >
      {monthMarks(span).map((mark) => (
        <span
          key={mark.atMs}
          style={labelPlace(fractionOf(mark.atMs, span))}
          className={cn(
            "absolute top-0 max-sm:[&:nth-child(even)]:hidden",
            mark.atMs < nowMs ? "text-ink-sepia" : "text-muted-foreground",
          )}
        >
          {mark.label}
        </span>
      ))}
    </div>
  );
}
