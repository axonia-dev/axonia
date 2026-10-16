import type { Ref } from "react";

import { shortDayLabel } from "@/features/events/ui/schedule-dates";
import { rowGridClass } from "@/features/events/ui/timeline/TimelineRow";
import { cn } from "@/lib/cn";

export function TodayOrigin({
  todayIso,
  atMs,
  ref,
}: Readonly<{ todayIso: string; atMs: number; ref?: Ref<HTMLDivElement> }>) {
  const label = shortDayLabel(todayIso);
  return (
    <div
      ref={ref}
      data-at={atMs}
      className={cn(rowGridClass, "items-center pb-3")}
    >
      <span className="bg-primary text-primary-foreground justify-self-end rounded-full px-2 py-0.5 text-xs font-semibold">
        Today
      </span>
      <span
        aria-hidden="true"
        className="timeline-beacon bg-primary relative left-[3px] size-3 rounded-full"
      />
      <span className="text-muted-foreground text-sm">{label}</span>
    </div>
  );
}

export function MonthRow({ label }: Readonly<{ label: string }>) {
  return (
    <li
      role="presentation"
      aria-hidden="true"
      className={cn("timeline-row pt-1 pb-2", rowGridClass)}
    >
      <span className="font-display text-muted-foreground justify-self-end text-[11px] font-bold tracking-widest uppercase">
        {label}
      </span>
    </li>
  );
}

export function GapRow({ weeks }: Readonly<{ weeks: number }>) {
  return (
    <li
      role="presentation"
      aria-hidden="true"
      className={cn("timeline-row pb-3", rowGridClass)}
    >
      <span className="text-muted-foreground col-start-3 flex items-center gap-2 text-[11px]">
        {/* A gap needs 14+ days, so weeks is always >= 2 and plural. */}
        <span>{weeks} quiet weeks</span>
        <span className="border-border-strong flex-1 border-t border-dotted" />
      </span>
    </li>
  );
}

export function StateMessages({
  isLoading,
  isError,
  count,
}: Readonly<{ isLoading: boolean; isError: boolean; count: number }>) {
  if (isLoading)
    return <p className="text-muted-foreground pb-3 text-sm">Loading…</p>;
  if (isError) {
    return (
      <p role="alert" className="text-danger pb-3 text-sm">
        Could not load schedule.
      </p>
    );
  }
  if (count === 0) {
    return (
      <p className="text-muted-foreground pb-3 text-sm">
        No scheduled sessions in this window.
      </p>
    );
  }
  return null;
}
