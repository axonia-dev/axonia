import { formatAverage } from "@/components/patterns/average-bars/format-average";

const BARS = Array.from({ length: 10 }, (_, index) => index + 1);

function filledPercent(value: number | null, bar: number): number {
  if (value === null) return 0;
  return Math.round(Math.min(1, Math.max(0, value - (bar - 1))) * 100);
}

function spokenLabel(label: string, value: number | null): string {
  if (value === null) return `${label} no answers`;
  return `${label} ${formatAverage(value)} out of 10`;
}

/**
 * The answer control's ten rising bars, filled to an average: whole bars up to its integer part,
 * then the next one filled from the left by the remainder.
 */
export function AverageBars({
  value,
  label,
}: Readonly<{ value: number | null; label: string }>) {
  return (
    <span
      role="img"
      aria-label={spokenLabel(label, value)}
      className="flex h-7 min-w-0 items-end gap-[3px]"
    >
      {BARS.map((bar) => {
        const filled = filledPercent(value, bar);
        return (
          <span
            key={bar}
            data-bar
            className="bg-data-area relative flex-1 overflow-hidden rounded-[4px]"
            style={{ height: `${8 + bar * 2}px` }}
          >
            {filled > 0 && (
              <span
                data-filled={filled}
                className="from-primary/45 to-data-1 absolute inset-0 bg-linear-to-t"
                style={{ clipPath: `inset(0 ${100 - filled}% 0 0)` }}
              />
            )}
          </span>
        );
      })}
    </span>
  );
}
