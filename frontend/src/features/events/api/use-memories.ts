import { useInfiniteQuery } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { scheduleKeys } from "@/features/events/api/query-keys";
import type { MemoryCursor } from "@/features/events/domain/memory";

const gateway = httpEventsGateway;

export const MEMORY_PAGE_SIZE = 20;

export function useMemories(slug: string) {
  return useInfiniteQuery({
    queryKey: scheduleKeys.memories(slug),
    queryFn: ({ pageParam }) =>
      gateway.getMemories(slug, pageParam, MEMORY_PAGE_SIZE),
    initialPageParam: null as MemoryCursor | null,
    getNextPageParam: (last) => last.next,
  });
}
