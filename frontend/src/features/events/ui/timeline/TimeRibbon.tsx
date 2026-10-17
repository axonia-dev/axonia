import { Undo2 } from "lucide-react";
import type { CSSProperties, KeyboardEvent } from "react";
import { useRef, useState } from "react";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import {
  fractionOf,
  RIBBON_WINDOW_MS,
  stepMonth,
  stepTick,
  ticksIn,
} from "@/features/events/domain/ribbon";
import { upcomingGlow } from "@/features/events/domain/upcoming-glow";
import {
  Cover,
  MonthLabels,
  percent,
  StepButton,
} from "@/features/events/ui/timeline/ribbon-parts";
import { useScrubbing } from "@/features/events/ui/timeline/use-scrubbing";
import { useStuck } from "@/features/events/ui/timeline/use-stuck";
import { cn } from "@/lib/cn";

interface Props {
  /** The window drawn on the track. */
  span: RibbonSpan;
  /** Everything the window can roll over: the slider's range. */
  bounds: RibbonSpan;
  /** Every loaded tick, inside the window and around it. */
  ticks: RibbonTick[];
  nowMs: number;
  lensMs: number;
  valueText: (atMs: number) => string;
  onScrub: (atMs: number) => void;
  /** Rolls the window by a signed amount, while a held pointer rests on an edge. */
  onSlide: (deltaMs: number) => void;
  /** The pointer took or let go of the ribbon. */
  onHold: (held: boolean) => void;
  /** Brings today back into the window; offered only while today is out of it. */
  onBackToToday: () => void;
}

const msOf = (tick: RibbonTick | null | undefined) =>
  tick ? new Date(tick.at).getTime() : null;

/** The lens stays whole inside the track at either end. */
const lensLeft = (fraction: number) =>
  `clamp(24px, ${percent(fraction)}, calc(100% - 24px))`;

type KeyStep = (ticks: RibbonTick[], from: number) => RibbonTick | null;

const KEY_STEPS = new Map<string, { step: KeyStep; direction: 1 | -1 }>([
  ["ArrowRight", { step: (t, from) => stepTick(t, from, 1), direction: 1 }],
  ["ArrowUp", { step: (t, from) => stepTick(t, from, 1), direction: 1 }],
  ["ArrowLeft", { step: (t, from) => stepTick(t, from, -1), direction: -1 }],
  ["ArrowDown", { step: (t, from) => stepTick(t, from, -1), direction: -1 }],
  ["PageUp", { step: (t, from) => stepMonth(t, from, 1), direction: 1 }],
  ["PageDown", { step: (t, from) => stepMonth(t, from, -1), direction: -1 }],
]);

interface KeyContext {
  ticks: RibbonTick[];
  bounds: RibbonSpan;
  lensMs: number;
  nowMs: number;
}

/**
 * What a key does: move the lens to an instant, or, for a step with no loaded session that way,
 * roll the window half its width so the years beyond load. Null for a key the ribbon leaves alone.
 */
function keyAction(
  key: string,
  context: KeyContext,
): { atMs: number } | { rollMs: number } | null {
  if (key === "t" || key === "T") return { atMs: context.nowMs };
  if (key === "Home") return { atMs: context.bounds.fromMs };
  if (key === "End") return { atMs: context.bounds.toMs };
  const keyStep = KEY_STEPS.get(key);
  if (keyStep === undefined) return null;
  const atMs = msOf(keyStep.step(context.ticks, context.lensMs));
  return atMs === null
    ? { rollMs: (keyStep.direction * RIBBON_WINDOW_MS) / 2 }
    : { atMs };
}

function tickStyle(tick: RibbonTick, span: RibbonSpan, nowMs: number) {
  const style: CSSProperties = {
    left: `clamp(7px, ${percent(fractionOf(msOf(tick) ?? 0, span))}, calc(100% - 7px))`,
  };
  if (tick.kind !== "UPCOMING") return style;
  const strength = upcomingGlow(tick.at, new Date(nowMs));
  return {
    ...style,
    opacity: 0.55 + 0.45 * strength,
    boxShadow: `0 0 6px color-mix(in srgb, var(--primary) ${Math.round(60 * strength)}%, transparent)`,
  };
}

const TICK_CLASS: Record<RibbonTick["kind"], string> = {
  MEMORY: "bg-ink-sepia/80 h-3 w-1",
  UPCOMING: "bg-focus-ring h-3.5 w-1",
  PROJECTED: "bg-muted-foreground/85 h-2 w-1",
};

export function TimeRibbon({
  span,
  bounds,
  ticks,
  nowMs,
  lensMs,
  valueText,
  onScrub,
  onSlide,
  onHold,
  onBackToToday,
}: Readonly<Props>) {
  const shown = ticksIn(ticks, span);
  const pointer = useScrubbing({
    span,
    ticks: shown,
    onScrub,
    onSlide,
    onHold,
  });
  const nowInView = nowMs >= span.fromMs && nowMs <= span.toMs;
  const [sentinel, setSentinel] = useState<HTMLDivElement | null>(null);
  const stuck = useStuck(sentinel);
  const slider = useRef<HTMLDivElement>(null);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const action = keyAction(event.key, { ticks, bounds, lensMs, nowMs });
    if (action === null) return;
    event.preventDefault();
    if ("atMs" in action) onScrub(action.atMs);
    else onSlide(action.rollMs);
  }

  // The button leaves with today's return, so focus stays on the ribbon rather than falling away.
  function backToToday() {
    onBackToToday();
    slider.current?.focus();
  }

  return (
    <>
      <div ref={setSentinel} aria-hidden="true" className="-mb-px h-px" />
      <div
        data-time-ribbon=""
        className="sticky top-0 z-10 mb-3 px-1 pt-2 pb-1.5"
      >
        <Cover shown={stuck} />
        <div className="flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <div
              ref={slider}
              role="slider"
              tabIndex={0}
              aria-label="Schedule timeline"
              aria-valuemin={bounds.fromMs}
              aria-valuemax={bounds.toMs}
              aria-valuenow={Math.min(
                bounds.toMs,
                Math.max(bounds.fromMs, lensMs),
              )}
              aria-valuetext={valueText(lensMs)}
              onKeyDown={onKeyDown}
              {...pointer}
              className="focus-visible:ring-focus-ring relative h-11 cursor-ew-resize touch-none rounded-lg outline-none select-none focus-visible:ring-2"
            >
              <div
                data-ribbon-track=""
                aria-hidden="true"
                className="border-border-strong absolute inset-x-0 top-[5px] h-[34px] rounded-lg border"
              />
              <span
                aria-hidden="true"
                style={{ left: lensLeft(fractionOf(lensMs, span)) }}
                data-ribbon-lens=""
                className="border-focus-ring bg-primary/6 pointer-events-none absolute inset-y-0 z-10 w-[46px] -translate-x-1/2 rounded-lg border-[1.5px] motion-safe:transition-[left] motion-safe:duration-75"
              />
              <div
                data-ribbon-marks=""
                className="pointer-events-none absolute inset-x-0 top-[5px] z-20 h-[34px] overflow-hidden rounded-lg"
              >
                {shown.map((tick, index) => (
                  <span
                    key={`${tick.kind}-${tick.at}-${index}`}
                    data-kind={tick.kind}
                    aria-hidden="true"
                    style={tickStyle(tick, span, nowMs)}
                    className={cn(
                      "absolute bottom-1 -translate-x-1/2 rounded-sm",
                      TICK_CLASS[tick.kind],
                    )}
                  />
                ))}
                {nowInView && (
                  <span
                    data-now-line=""
                    aria-hidden="true"
                    style={{ left: percent(fractionOf(nowMs, span)) }}
                    className="bg-primary absolute inset-y-0 w-0.5 -translate-x-1/2"
                  />
                )}
              </div>
            </div>
            <MonthLabels span={span} nowMs={nowMs} />
          </div>
          {!nowInView && (
            <StepButton onPress={backToToday}>
              <Undo2 size={14} aria-hidden="true" />
              <span className="max-sm:sr-only">Back to today</span>
            </StepButton>
          )}
        </div>
      </div>
    </>
  );
}
