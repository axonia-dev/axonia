import { ChevronLeft, Undo2 } from "lucide-react";
import type {
  CSSProperties,
  KeyboardEvent,
  PointerEvent,
  ReactNode,
} from "react";
import { useRef } from "react";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import {
  atFraction,
  fractionOf,
  monthMarks,
  nearestTick,
  stepMonth,
  stepTick,
  ticksIn,
} from "@/features/events/domain/ribbon";
import { upcomingGlow } from "@/features/events/domain/upcoming-glow";
import { cn } from "@/lib/cn";

interface Props {
  span: RibbonSpan;
  ticks: RibbonTick[];
  nowMs: number;
  lensMs: number;
  valueText: (atMs: number) => string;
  onScrub: (atMs: number) => void;
  onFurtherBack?: () => void;
  onBackToToday?: () => void;
}

const msOf = (tick: RibbonTick | null | undefined) =>
  tick ? new Date(tick.at).getTime() : null;

const percent = (fraction: number) => `${fraction * 100}%`;

/** Centred under its month, sliding to left or right alignment toward the edges so it is never cut. */
const labelPlace = (fraction: number): CSSProperties => ({
  left: percent(fraction),
  transform: `translateX(-${percent(fraction)})`,
});

/** The lens stays whole inside the track at either end. */
const lensLeft = (fraction: number) =>
  `clamp(23px, ${percent(fraction)}, calc(100% - 23px))`;

type KeyStep = (ticks: RibbonTick[], from: number) => RibbonTick | null;

const KEY_STEPS = new Map<string, KeyStep>([
  ["ArrowRight", (ticks, from) => stepTick(ticks, from, 1)],
  ["ArrowUp", (ticks, from) => stepTick(ticks, from, 1)],
  ["ArrowLeft", (ticks, from) => stepTick(ticks, from, -1)],
  ["ArrowDown", (ticks, from) => stepTick(ticks, from, -1)],
  ["PageUp", (ticks, from) => stepMonth(ticks, from, 1)],
  ["PageDown", (ticks, from) => stepMonth(ticks, from, -1)],
  ["Home", (ticks) => ticks[0] ?? null],
  ["End", (ticks) => ticks.at(-1) ?? null],
]);

/** The instant a key moves the lens to, or null for a key the ribbon leaves alone. */
function keyTarget(
  key: string,
  ticks: RibbonTick[],
  { lensMs, nowMs }: Readonly<{ lensMs: number; nowMs: number }>,
): number | null {
  if (key === "t" || key === "T") return nowMs;
  const step = KEY_STEPS.get(key);
  return step === undefined ? null : msOf(step(ticks, lensMs));
}

function tickStyle(tick: RibbonTick, span: RibbonSpan, nowMs: number) {
  const style: CSSProperties = {
    left: percent(fractionOf(msOf(tick) ?? 0, span)),
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
  MEMORY: "bg-ink-sepia-soft h-3 w-1",
  UPCOMING: "bg-primary h-3.5 w-1",
  PROJECTED: "bg-muted-foreground/35 h-2 w-1",
};

function StepButton({
  onPress,
  children,
}: Readonly<{ onPress: () => void; children: ReactNode }>) {
  return (
    <button
      type="button"
      onClick={onPress}
      className="border-border-strong text-primary-strong hover:border-primary hover:bg-primary/5 inline-flex shrink-0 cursor-pointer items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold whitespace-nowrap transition-colors"
    >
      {children}
    </button>
  );
}

function MonthLabels({
  span,
  nowMs,
}: Readonly<{ span: RibbonSpan; nowMs: number }>) {
  return (
    <div
      aria-hidden="true"
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

function useScrubbing(
  span: RibbonSpan,
  ticks: RibbonTick[],
  onScrub: (atMs: number) => void,
) {
  const held = useRef(false);
  const last = useRef<number | null>(null);

  function scrubTo(event: PointerEvent<HTMLDivElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width <= 0) return;
    const fraction = (event.clientX - rect.left) / rect.width;
    const at = msOf(nearestTick(ticks, atFraction(fraction, span)));
    if (at === null || at === last.current) return;
    last.current = at;
    onScrub(at);
  }

  return {
    onPointerDown: (event: PointerEvent<HTMLDivElement>) => {
      held.current = true;
      last.current = null;
      event.currentTarget.setPointerCapture(event.pointerId);
      scrubTo(event);
    },
    onPointerMove: (event: PointerEvent<HTMLDivElement>) => {
      if (held.current) scrubTo(event);
    },
    onPointerUp: (event: PointerEvent<HTMLDivElement>) => {
      held.current = false;
      event.currentTarget.releasePointerCapture(event.pointerId);
    },
    onPointerCancel: () => {
      held.current = false;
    },
  };
}

export function TimeRibbon({
  span,
  ticks,
  nowMs,
  lensMs,
  valueText,
  onScrub,
  onFurtherBack,
  onBackToToday,
}: Readonly<Props>) {
  const shown = ticksIn(ticks, span);
  const pointer = useScrubbing(span, shown, onScrub);
  const nowFraction = fractionOf(nowMs, span);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const at = keyTarget(event.key, shown, { lensMs, nowMs });
    if (at === null) return;
    event.preventDefault();
    onScrub(at);
  }

  return (
    <div
      data-time-ribbon=""
      className="bg-background/90 border-border sticky top-0 z-10 mb-3 border-b px-1 pt-2 pb-1.5 backdrop-blur"
    >
      <div className="flex items-center gap-2">
        {onFurtherBack && (
          <StepButton onPress={onFurtherBack}>
            <ChevronLeft size={14} aria-hidden="true" />
            Further back
          </StepButton>
        )}
        <div className="min-w-0 flex-1">
          <div
            role="slider"
            tabIndex={0}
            aria-label="Schedule timeline"
            aria-valuemin={span.fromMs}
            aria-valuemax={span.toMs}
            aria-valuenow={Math.min(span.toMs, Math.max(span.fromMs, lensMs))}
            aria-valuetext={valueText(lensMs)}
            onKeyDown={onKeyDown}
            {...pointer}
            className="focus-visible:ring-primary/40 relative h-11 cursor-ew-resize touch-none rounded-lg outline-none select-none focus-visible:ring-2"
          >
            <div className="border-border absolute inset-x-0 top-[5px] h-[34px] overflow-hidden rounded-lg border">
              <div
                aria-hidden="true"
                className="bg-paper absolute inset-y-0 left-0"
                style={{ width: percent(nowFraction) }}
              />
              <div
                aria-hidden="true"
                className="bg-primary/7 absolute inset-y-0 right-0"
                style={{ left: percent(nowFraction) }}
              />
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
              <span
                data-now-line=""
                aria-hidden="true"
                style={{ left: percent(nowFraction) }}
                className="bg-primary absolute inset-y-0 w-0.5 -translate-x-1/2"
              />
            </div>
            <span
              aria-hidden="true"
              style={{ left: lensLeft(fractionOf(lensMs, span)) }}
              className="border-foreground bg-foreground/5 pointer-events-none absolute inset-y-0 w-[46px] -translate-x-1/2 rounded-lg border-2 motion-safe:transition-[left] motion-safe:duration-75"
            />
          </div>
          <MonthLabels span={span} nowMs={nowMs} />
        </div>
        {onBackToToday && (
          <StepButton onPress={onBackToToday}>
            <Undo2 size={14} aria-hidden="true" />
            Back to today
          </StepButton>
        )}
      </div>
    </div>
  );
}
