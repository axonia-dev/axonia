import { ChevronLeft, Undo2 } from "lucide-react";
import type { CSSProperties, KeyboardEvent, PointerEvent } from "react";
import { useRef, useState } from "react";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import {
  atFraction,
  fractionOf,
  nearestTick,
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
import { useStuck } from "@/features/events/ui/timeline/use-stuck";
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

/** The lens stays whole inside the track at either end. */
const lensLeft = (fraction: number) =>
  `clamp(24px, ${percent(fraction)}, calc(100% - 24px))`;

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
  const [sentinel, setSentinel] = useState<HTMLDivElement | null>(null);
  const stuck = useStuck(sentinel);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const at = keyTarget(event.key, shown, { lensMs, nowMs });
    if (at === null) return;
    event.preventDefault();
    onScrub(at);
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
                <span
                  data-now-line=""
                  aria-hidden="true"
                  style={{ left: percent(nowFraction) }}
                  className="bg-primary absolute inset-y-0 w-0.5 -translate-x-1/2"
                />
              </div>
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
    </>
  );
}
