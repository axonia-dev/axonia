import type { Memory, MemoryPage } from "@/features/events/domain/memory";
import { NO_TRACES } from "@/features/events/domain/memory";
import { hasEnded } from "@/features/events/domain/next-countdown";
import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  entryKey,
  entrySortKey,
  isShownOnTimeline,
  monthKey,
  monthLabel,
  quietWeeks,
} from "@/features/events/ui/timeline/timeline";

type MemoryTimelineItem =
  | { kind: "memory"; memory: Memory; arriving: boolean }
  | { kind: "month"; label: string }
  | { kind: "gap"; weeks: number };

export function splitEnded(
  entries: ScheduleEntry[],
  now: Date,
): { ended: ScheduleEntry[]; ahead: ScheduleEntry[] } {
  const ended: ScheduleEntry[] = [];
  const ahead: ScheduleEntry[] = [];
  for (const entry of entries) {
    (hasEnded(entry, now) ? ended : ahead).push(entry);
  }
  return { ended, ahead };
}

function newestFirst(a: Memory, b: Memory): number {
  return entrySortKey(b.entry).localeCompare(entrySortKey(a.entry));
}

/** A projected occurrence that passed was never opened, so it leaves nothing to remember. */
function becomesMemory(entry: ScheduleEntry): entry is ScheduleEntry & {
  sessionId: number;
} {
  return entry.sessionId !== null && isShownOnTimeline(entry);
}

export function mergeMemories(
  local: ScheduleEntry[],
  pages: MemoryPage[],
): Memory[] {
  const bySession = new Map<string, Memory>();
  for (const entry of local.filter(becomesMemory)) {
    bySession.set(entryKey(entry, 0), { entry, traces: NO_TRACES });
  }
  for (const memory of pages.flatMap((page) => page.items)) {
    bySession.set(entryKey(memory.entry, 0), memory);
  }
  const memories = [...bySession.values()];
  memories.sort(newestFirst);
  return memories;
}

export function buildMemoryTimeline(
  memories: Memory[],
  arrivingIds: ReadonlySet<number>,
): MemoryTimelineItem[] {
  const oldestFirst = [...memories];
  oldestFirst.sort((a, b) => newestFirst(b, a));
  const items: MemoryTimelineItem[] = [];
  let prevIso: string | null = null;
  for (const memory of oldestFirst) {
    const iso = entryDayIso(memory.entry);
    if (iso === "") continue;
    const weeks = prevIso === null ? null : quietWeeks(prevIso, iso);
    if (weeks !== null) items.push({ kind: "gap", weeks });
    if (prevIso === null || monthKey(prevIso) !== monthKey(iso)) {
      items.push({ kind: "month", label: monthLabel(iso) });
    }
    const { sessionId } = memory.entry;
    items.push({
      kind: "memory",
      memory,
      arriving: sessionId !== null && arrivingIds.has(sessionId),
    });
    prevIso = iso;
  }
  return items;
}
