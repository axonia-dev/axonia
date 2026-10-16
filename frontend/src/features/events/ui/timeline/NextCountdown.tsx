import {
  countdownParts,
  countdownPhase,
  countdownWakeAt,
  lastHourLabel,
} from "@/features/events/domain/next-countdown";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { useNow, useWakeAt } from "@/lib/use-wake-at";

export function NextCountdown({ entry }: Readonly<{ entry: ScheduleEntry }>) {
  const now = useNow();
  useWakeAt(countdownWakeAt(entry, now));
  const phase = countdownPhase(entry, now);
  if (phase === null || phase.kind === "ended") return null;
  if (phase.kind === "running") {
    return (
      <p className="text-primary-strong mt-2 inline-flex items-center gap-2 text-xs font-semibold">
        <span
          aria-hidden="true"
          className="timeline-beacon bg-primary size-2 rounded-full"
        />
        Running now
      </p>
    );
  }
  if (phase.kind === "last-hour") {
    return (
      <p className="text-primary-strong font-display mt-2 text-sm font-semibold tabular-nums">
        {lastHourLabel(phase.msLeft)}
      </p>
    );
  }
  const parts = countdownParts(phase.msLeft);
  const tiles: [number, string][] = [
    [parts.days, "d"],
    [parts.hours, "h"],
    [parts.minutes, "m"],
    [parts.seconds, "s"],
  ];
  return (
    <div
      aria-hidden="true"
      className="font-display mt-2 flex gap-1 tabular-nums"
    >
      {tiles.map(([value, unit], i) => (
        <span
          key={unit}
          className="bg-primary/10 text-primary-strong min-w-8 rounded-md px-1.5 py-0.5 text-center text-sm font-semibold"
        >
          {i === 0 ? value : String(value).padStart(2, "0")}
          <span className="ml-px text-[10px] opacity-75">{unit}</span>
        </span>
      ))}
    </div>
  );
}
