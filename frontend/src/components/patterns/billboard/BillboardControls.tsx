import { ChevronLeft, ChevronRight, Pause, Play } from "lucide-react";
import { useEffect, useRef } from "react";

import { cn } from "@/lib/cn";

import type { BillboardRotation } from "./use-billboard-rotation";

export type BillboardControlsTone = "overlay" | "surface";

export type BillboardDot = { id: string; label: string };

type ToneClasses = {
  glass: string;
  button: string;
  track: string;
  idle: string;
  current: string;
  fill: string;
};

const OVERLAY: ToneClasses = {
  glass:
    "border border-white/15 bg-black/45 text-white shadow-sm backdrop-blur-md",
  button: "hover:bg-black/65 aria-disabled:hover:bg-black/45",
  track: "group-focus-visible:ring-offset-black/40",
  idle: "bg-white/35 group-hover:bg-white/60",
  current: "bg-white",
  fill: "bg-white",
};

const SURFACE: ToneClasses = {
  glass:
    "border-border-strong bg-surface/85 text-foreground border shadow-sm backdrop-blur-md",
  button: "hover:bg-surface-sunken aria-disabled:hover:bg-surface/85",
  track: "group-focus-visible:ring-offset-surface",
  idle: "bg-border-strong group-hover:bg-control-edge",
  current: "bg-primary",
  fill: "bg-primary",
};

function roundButton(tone: ToneClasses): string {
  return cn(
    tone.glass,
    tone.button,
    "inline-flex size-9 flex-none cursor-pointer items-center justify-center rounded-full outline-none transition-[background-color,opacity] duration-200",
    "aria-disabled:cursor-default aria-disabled:opacity-35",
    "focus-visible:ring-focus-ring focus-visible:ring-offset-background focus-visible:ring-2 focus-visible:ring-offset-2",
  );
}

export function BillboardControls({
  dots,
  tone: toneName,
  rotation,
  goTo,
  className,
}: Readonly<{
  dots: readonly BillboardDot[];
  tone: BillboardControlsTone;
  rotation: BillboardRotation;
  goTo: (target: number) => void;
  className?: string | undefined;
}>) {
  const { index, paused, rotates, togglePaused } = rotation;
  const count = dots.length;
  const tone = toneName === "surface" ? SURFACE : OVERLAY;
  const button = roundButton(tone);
  return (
    <div
      className={cn(
        "relative mt-3 flex items-center justify-center gap-2 md:absolute md:right-6 md:bottom-6 md:mt-0 md:justify-end",
        className,
      )}
    >
      {rotates && (
        <button
          type="button"
          aria-label={paused ? "Play" : "Pause"}
          onClick={togglePaused}
          className={button}
        >
          {paused ? (
            <Play size={15} aria-hidden className="translate-x-px" />
          ) : (
            <Pause size={15} aria-hidden />
          )}
        </button>
      )}
      <button
        type="button"
        aria-label="Previous slide"
        aria-disabled={index === 0}
        onClick={() => {
          if (index > 0) goTo(index - 1);
        }}
        className={button}
      >
        <ChevronLeft size={18} aria-hidden />
      </button>
      <div
        className={cn(
          tone.glass,
          "flex h-9 items-center gap-1.5 rounded-full px-3.5",
        )}
      >
        {dots.map((dot, i) => (
          <Dot
            key={dot.id}
            label={dot.label}
            tone={tone}
            active={i === index}
            rotation={rotation}
            onSelect={() => goTo(i)}
          />
        ))}
      </div>
      <button
        type="button"
        aria-label="Next slide"
        aria-disabled={index === count - 1}
        onClick={() => {
          if (index < count - 1) goTo(index + 1);
        }}
        className={button}
      >
        <ChevronRight size={18} aria-hidden />
      </button>
    </div>
  );
}

function Dot({
  label,
  tone,
  active,
  rotation,
  onSelect,
}: Readonly<{
  label: string;
  tone: ToneClasses;
  active: boolean;
  rotation: BillboardRotation;
  onSelect: () => void;
}>) {
  const filling = active && rotation.advancing;
  return (
    <button
      type="button"
      aria-label={label}
      aria-current={active}
      onClick={onSelect}
      className="group relative flex h-6 cursor-pointer items-center outline-none"
    >
      <span
        className={cn(
          "relative block h-1 overflow-hidden rounded-full transition-colors duration-300 motion-safe:transition-[width,background-color]",
          "group-focus-visible:ring-focus-ring group-focus-visible:ring-2 group-focus-visible:ring-offset-1",
          tone.track,
          active ? "w-7" : "w-3.5",
          active && !filling ? tone.current : tone.idle,
        )}
      >
        {filling && (
          <ProgressFill
            durationMs={rotation.intervalMs}
            className={tone.fill}
          />
        )}
      </span>
    </button>
  );
}

function ProgressFill({
  durationMs,
  className,
}: Readonly<{ durationMs: number; className: string }>) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const fill = ref.current;
    if (fill === null || typeof fill.animate !== "function") return undefined;
    const animation = fill.animate(
      [{ transform: "scaleX(0)" }, { transform: "scaleX(1)" }],
      { duration: durationMs, easing: "linear", fill: "forwards" },
    );
    return () => animation.cancel();
  }, [durationMs]);
  return (
    <span
      ref={ref}
      data-billboard-progress
      className={cn(
        "absolute inset-0 origin-left scale-x-0 rounded-full",
        className,
      )}
    />
  );
}
