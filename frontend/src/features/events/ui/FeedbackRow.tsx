import { Lightbulb, Smile } from "lucide-react";
import type { KeyboardEvent, PointerEvent } from "react";
import { useRef, useState } from "react";
import {
  Label,
  RadioButton,
  RadioField,
  RadioGroup,
} from "react-aria-components";

import {
  cascade,
  CASCADE_STEP_MS,
  motesFromBar,
  wiggle,
} from "@/features/events/ui/feedback-motion";
import type {
  BarTone,
  FeedbackQuestion,
} from "@/features/events/ui/feedback-scrub";
import {
  barTone,
  FEEDBACK_SCALE,
  feedbackWord,
  valueAt,
} from "@/features/events/ui/feedback-scrub";
import { FlipWord } from "@/features/events/ui/FlipWord";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

interface Props {
  question: FeedbackQuestion;
  label: string;
  ends: readonly [string, string];
  value: number | null;
  onChange: (value: number) => void;
}

const BARS = Array.from({ length: FEEDBACK_SCALE }, (_, index) => index + 1);
const STEPS: ReadonlyMap<string, number> = new Map([
  ["ArrowRight", 1],
  ["ArrowUp", 1],
  ["ArrowLeft", -1],
  ["ArrowDown", -1],
]);

function toneClass(tone: BarTone): string {
  if (tone === "on") return "from-primary/45 to-data-1 bg-linear-to-t";
  return tone === "peek" ? "bg-primary/35" : "bg-data-area";
}

function barHeight(bar: number): string {
  return `${10 + bar * 3.8}px`;
}

/**
 * One question answered on ten rising bars: a tap or a press-and-drag picks an answer, the arrow
 * keys step it, and every answer reads back as a word, never a digit.
 */
export function FeedbackRow({
  question,
  label,
  ends,
  value,
  onChange,
}: Readonly<Props>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [preview, setPreview] = useState<number | null>(null);
  const dragging = useRef(false);
  const barRefs = useRef<(HTMLSpanElement | null)[]>([]);
  const iconRef = useRef<HTMLSpanElement | null>(null);
  const rowRef = useRef<HTMLDivElement | null>(null);

  const shownValue = preview ?? value;
  const word = shownValue === null ? "" : feedbackWord(question, shownValue);
  const spoken = value === null ? "" : feedbackWord(question, value);
  const lit = value ?? 0;
  const Icon = question === "enjoyed" ? Smile : Lightbulb;

  function commit(next: number) {
    const previous = value;
    onChange(next);
    if (reduced) return;
    cascade(barRefs.current, next);
    if (next < 8) return;
    wiggle(iconRef.current);
    if (next === previous) return;
    const bar = barRefs.current[next - 1] ?? null;
    globalThis.setTimeout(() => {
      motesFromBar(bar, next);
    }, next * CASCADE_STEP_MS);
  }

  function focusRadio(next: number) {
    rowRef.current
      ?.querySelectorAll("input")
      .item(next - 1)
      .focus({ preventScroll: true });
  }

  function previewAt(event: PointerEvent<HTMLDivElement>): number | null {
    const next = valueAt(
      event.clientX,
      event.currentTarget.getBoundingClientRect(),
    );
    if (next === null || next === preview) return next;
    setPreview(next);
    if (dragging.current && "vibrate" in navigator) navigator.vibrate(6);
    return next;
  }

  /** Owns the whole press once the row has layout, so a radio's own press never commits the bar
   *  a drag started on after the drag has moved on. */
  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    if (previewAt(event) === null) return;
    event.stopPropagation();
    dragging.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    if (!dragging.current) return;
    dragging.current = false;
    const next = valueAt(
      event.clientX,
      event.currentTarget.getBoundingClientRect(),
    );
    setPreview(null);
    if (next === null) return;
    commit(next);
    focusRadio(next);
  }

  function endPreview() {
    dragging.current = false;
    setPreview(null);
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = STEPS.get(event.key);
    if (step === undefined) return;
    event.preventDefault();
    event.stopPropagation();
    const next = Math.max(1, Math.min(FEEDBACK_SCALE, (value ?? 0) + step));
    commit(next);
    focusRadio(next);
  }

  return (
    <RadioGroup
      value={value === null ? null : String(value)}
      onChange={(next) => {
        commit(Number(next));
      }}
      orientation="horizontal"
      className="relative"
    >
      <div className="mb-2 flex min-h-5 items-center justify-between gap-3">
        <Label className="text-foreground text-[13px] font-semibold">
          {label}
        </Label>
        <FlipWord word={word} spoken={spoken} still={reduced} />
      </div>
      <div className="flex items-end gap-3">
        <div
          ref={rowRef}
          role="presentation"
          className="flex h-12 min-w-0 flex-1 cursor-pointer touch-none items-end gap-[5px] select-none"
          onPointerDownCapture={onPointerDown}
          onPointerMoveCapture={previewAt}
          onPointerUp={onPointerUp}
          onPointerCancel={endPreview}
          onPointerLeave={() => {
            if (!dragging.current) setPreview(null);
          }}
          onKeyDown={onKeyDown}
        >
          {BARS.map((bar) => (
            <RadioField
              key={bar}
              value={String(bar)}
              className="flex h-full min-w-0 flex-1"
            >
              <RadioButton className="flex h-full w-full cursor-pointer items-end outline-none">
                {({ isFocusVisible }) => (
                  <>
                    <span
                      ref={(element) => {
                        barRefs.current[bar - 1] = element;
                      }}
                      data-bar
                      data-tone={barTone(bar, value, preview)}
                      className={cn(
                        "block w-full origin-bottom rounded-[7px] motion-safe:transition-[background-color,transform] motion-safe:duration-300 motion-safe:ease-[cubic-bezier(.34,1.7,.64,1)]",
                        toneClass(barTone(bar, value, preview)),
                        bar === preview && "scale-y-[1.12]",
                        isFocusVisible &&
                          "ring-focus-ring ring-offset-surface ring-2 ring-offset-2",
                      )}
                      style={{ height: barHeight(bar) }}
                    />
                    <span className="sr-only">
                      {feedbackWord(question, bar)}
                    </span>
                  </>
                )}
              </RadioButton>
            </RadioField>
          ))}
        </div>
        <span
          aria-hidden="true"
          className={cn(
            "grid size-[30px] flex-none place-items-center motion-safe:transition-[color,transform,filter] motion-safe:duration-300",
            lit >= 5 ? "text-data-1" : "text-muted-foreground",
            lit >= 9 &&
              "drop-shadow-[0_0_6px_color-mix(in_srgb,var(--primary)_70%,transparent)]",
          )}
          style={{ transform: `scale(${0.9 + lit * 0.03})` }}
        >
          <span ref={iconRef} className="grid place-items-center">
            <Icon size={22} strokeWidth={2} />
          </span>
        </span>
      </div>
      <div
        className={cn(
          "text-muted-foreground mt-1.5 mr-[42px] flex justify-between text-[11px] motion-safe:transition-opacity motion-safe:duration-300",
          value !== null && "opacity-0",
        )}
      >
        <span>{ends[0]}</span>
        <span>{ends[1]}</span>
      </div>
    </RadioGroup>
  );
}
