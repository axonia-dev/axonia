import type { ReactNode } from "react";

import {
  dayOfMonth,
  monthOf,
  weekdayOf,
} from "@/features/mentoring/ui/meeting-day";
import { cn } from "@/lib/cn";

type Dot = "ahead" | "today" | "owed" | "met";

const DOT = new Map<Dot, string>([
  [
    "ahead",
    "top-4 -left-[9px] size-4 bg-surface border-warning border-2 border-dashed",
  ],
  ["today", "top-[19px] -left-[6px] size-2.5 bg-foreground"],
  ["owed", "top-4 -left-[9px] size-4 bg-surface border-warning border-2"],
  ["met", "top-4 -left-[9px] size-4 bg-warning"],
]);

function When({ on }: Readonly<{ on?: string | undefined }>) {
  if (on === undefined) return <span aria-hidden className="hidden sm:block" />;
  return (
    <p aria-hidden className="hidden pt-3 text-right sm:block">
      <span className="font-display text-foreground block text-2xl leading-none font-bold">
        {dayOfMonth(on)}
      </span>
      <span className="text-muted-foreground text-xs">
        {monthOf(on)} · {weekdayOf(on)}
      </span>
    </p>
  );
}

/** One stop on the logbook's rail: its day beside the line, its dot on it, its content after. */
export function RailRow({
  on,
  dot,
  children,
}: Readonly<{
  on?: string | undefined;
  dot: Dot;
  children: ReactNode;
}>) {
  return (
    <li
      data-rail={dot}
      className="grid grid-cols-1 gap-x-4 sm:grid-cols-[5.5rem_minmax(0,1fr)]"
    >
      <When on={on} />
      <div className="border-border-strong relative border-l-2 border-dashed pb-5 pl-5">
        <span
          aria-hidden
          className={cn(
            "absolute rounded-full shadow-[0_0_0_4px_var(--surface)]",
            DOT.get(dot),
          )}
        />
        {children}
      </div>
    </li>
  );
}
