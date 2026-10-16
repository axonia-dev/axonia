import type { QueryClient } from "@tanstack/react-query";

import { scheduleKeys } from "@/features/events/api/query-keys";

const KIND_SEGMENT = 3;

/** Marks every community's loaded memories stale, leaving the schedule windows and the ribbon alone. */
export function invalidateMemories(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({
    queryKey: scheduleKeys.all,
    predicate: (query) => query.queryKey.at(KIND_SEGMENT) === "memories",
  });
}

/** Marks every loaded ribbon stale: a session that just ended turns its tick from upcoming to memory. */
export function invalidateRibbons(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({
    queryKey: scheduleKeys.all,
    predicate: (query) => query.queryKey.at(KIND_SEGMENT) === "ribbon",
  });
}
