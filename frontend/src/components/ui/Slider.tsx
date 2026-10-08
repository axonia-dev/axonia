import {
  Label,
  Slider as AriaSlider,
  SliderOutput,
  SliderThumb,
  SliderTrack,
} from "react-aria-components";

import { cn } from "@/lib/cn";

interface SliderProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  minValue: number;
  maxValue: number;
  step: number;
  /** Render the live value (and the thumb's aria text). Defaults to the number. */
  formatValue?: (value: number) => string;
  /**
   * Optional reference mark on the track (e.g. the remaining section budget).
   * Hidden automatically when it falls outside the [min, max] range.
   */
  markerValue?: number;
  /** Helper line under the track. */
  description?: string;
  /** Tone of the helper line — `warning` turns it amber (e.g. over budget). */
  descriptionTone?: "muted" | "warning";
  className?: string;
}

function percentOf(value: number, min: number, max: number): number {
  if (max <= min) {
    return 0;
  }
  return Math.min(1, Math.max(0, (value - min) / (max - min)));
}

/**
 * A single-value slider over a numeric range, styled with the app tokens. The
 * value is echoed in a `SliderOutput` for sighted and assistive users alike, and
 * the thumb steps by `step` with the keyboard. An optional `markerValue` paints a
 * reference tick on the track — used to show the remaining time budget so a slot
 * length can be set against it without a hard cap.
 */
export function Slider({
  label,
  value,
  onChange,
  minValue,
  maxValue,
  step,
  formatValue,
  markerValue,
  description,
  descriptionTone = "muted",
  className,
}: Readonly<SliderProps>) {
  const format = formatValue ?? ((v: number) => String(v));
  const showMarker =
    markerValue !== undefined &&
    markerValue > minValue &&
    markerValue < maxValue;

  return (
    <AriaSlider
      value={value}
      onChange={onChange}
      minValue={minValue}
      maxValue={maxValue}
      step={step}
      className={cn("flex flex-col gap-1", className)}
    >
      <div className="flex items-center justify-between">
        <Label className="text-foreground text-sm font-medium">{label}</Label>
        <SliderOutput className="text-foreground text-sm font-semibold">
          {({ state }) => format(state.getThumbValue(0))}
        </SliderOutput>
      </div>
      <SliderTrack className="relative h-5 w-full">
        {({ state }) => (
          <>
            <div className="bg-surface-sunken absolute top-1/2 h-2 w-full -translate-y-1/2 rounded-full" />
            <div
              className="bg-primary absolute top-1/2 h-2 -translate-y-1/2 rounded-full"
              style={{ width: `${state.getThumbPercent(0) * 100}%` }}
            />
            {showMarker && (
              <div
                aria-hidden
                className="bg-warning absolute top-1/2 h-3.5 w-0.5 -translate-x-1/2 -translate-y-1/2 rounded-full"
                style={{
                  left: `${percentOf(markerValue, minValue, maxValue) * 100}%`,
                }}
              />
            )}
            <SliderThumb className="border-primary focus-visible:ring-focus-ring top-1/2 h-4 w-4 rounded-full border-2 bg-white shadow-sm outline-none focus-visible:ring-2" />
          </>
        )}
      </SliderTrack>
      {description !== undefined && (
        <p
          className={cn(
            "text-xs",
            descriptionTone === "warning"
              ? "text-warning"
              : "text-muted-foreground",
          )}
        >
          {description}
        </p>
      )}
    </AriaSlider>
  );
}
