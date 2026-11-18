import { CalendarDays } from "lucide-react";

import { dayAndMonth } from "@/lib/declared-on";

/** The day an upcoming meeting means to cover a topic, small and quiet. */
export function PlannedDay({ day }: Readonly<{ day: string | null }>) {
  if (day === null) return null;
  return (
    <span className="text-muted-foreground inline-flex items-center gap-1 text-[11px] leading-tight whitespace-nowrap">
      <CalendarDays aria-hidden className="size-3" />
      <span className="sr-only">Planned </span>
      {dayAndMonth(day)}
    </span>
  );
}
