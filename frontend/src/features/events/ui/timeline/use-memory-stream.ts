import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { invalidateMemories } from "@/features/events/api/invalidate-memories";
import { useMemories } from "@/features/events/api/use-memories";
import { endOfEntry } from "@/features/events/domain/next-countdown";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  mergeMemories,
  splitEnded,
} from "@/features/events/ui/timeline/memory-timeline";
import { useNow, useWakeAt } from "@/lib/use-wake-at";

interface Arrivals {
  /** The ended sessions already known, or null before the schedule first loaded. */
  seen: ReadonlySet<number> | null;
  arriving: ReadonlySet<number>;
}

const NO_ARRIVALS: Arrivals = { seen: null, arriving: new Set() };

function nextEnd(ahead: ScheduleEntry[]): number | null {
  const ends = ahead.flatMap((entry) => endOfEntry(entry) ?? []);
  return ends.length === 0 ? null : Math.min(...ends);
}

function endedIds(ended: ScheduleEntry[]): number[] {
  return ended.flatMap((entry) => entry.sessionId ?? []);
}

/** The next arrivals state, or null when nothing changed. */
function nextArrivals(
  state: Arrivals,
  ids: number[],
  loaded: boolean,
): Arrivals | null {
  if (state.seen === null) {
    return loaded ? { seen: new Set(ids), arriving: state.arriving } : null;
  }
  const { seen } = state;
  const fresh = ids.filter((id) => !seen.has(id));
  if (fresh.length === 0) return null;
  return {
    seen: new Set([...seen, ...fresh]),
    arriving: new Set([...state.arriving, ...fresh]),
  };
}

/** Sessions that end while the page is open: they move above Today and fetch their traces. */
function useArrivals(
  ended: ScheduleEntry[],
  loaded: boolean,
): ReadonlySet<number> {
  const queryClient = useQueryClient();
  const [state, setState] = useState(NO_ARRIVALS);
  const next = nextArrivals(state, endedIds(ended), loaded);
  if (next !== null) setState(next);

  useEffect(() => {
    if (state.arriving.size > 0) void invalidateMemories(queryClient);
  }, [state.arriving, queryClient]);
  return state.arriving;
}

export function useMemoryStream(
  slug: string,
  schedule: ScheduleEntry[] | undefined,
) {
  const now = useNow();
  const { ended, ahead } = splitEnded(schedule ?? [], now);
  useWakeAt(nextEnd(ahead));
  const memoriesQuery = useMemories(slug);
  const arriving = useArrivals(ended, schedule !== undefined);
  const memories = mergeMemories(ended, memoriesQuery.data?.pages ?? []);
  return { now, ahead, memories, arriving, memoriesQuery };
}
