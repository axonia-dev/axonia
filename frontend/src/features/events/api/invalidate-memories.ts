import type { QueryClient } from "@tanstack/react-query";

import { scheduleKeys } from "@/features/events/api/query-keys";

const MEMORIES_SEGMENT = 3;

/** Marks every community's loaded memories stale, leaving the schedule windows and the ribbon alone. */
export function invalidateMemories(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({
    queryKey: scheduleKeys.all,
    predicate: (query) => query.queryKey[MEMORIES_SEGMENT] === "memories",
  });
}
