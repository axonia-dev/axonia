import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  entryAtMs,
  ribbonDayLabel,
} from "@/features/events/ui/timeline/timeline";

interface Context {
  labelOf: (entry: ScheduleEntry) => string;
  nowMs: number;
  todayIso: string;
}

const dayOf = (iso: string) => iso.slice(0, 10);

/** What the ribbon's lens reads: the session at that instant, "Today", or else the bare day. */
export function ribbonValueText(
  entries: ScheduleEntry[],
  { labelOf, nowMs, todayIso }: Readonly<Context>,
): (atMs: number) => string {
  const sessions = new Map<number, string>();
  for (const entry of entries) {
    const iso = entryDayIso(entry);
    if (iso !== "") {
      sessions.set(
        entryAtMs(entry),
        `${ribbonDayLabel(iso)}, ${labelOf(entry)}`,
      );
    }
  }
  return (atMs) => {
    const session = sessions.get(atMs);
    if (session !== undefined) return session;
    const iso = new Date(atMs).toISOString();
    if (atMs === nowMs || dayOf(iso) === dayOf(todayIso)) return "Today";
    return ribbonDayLabel(iso);
  };
}
