import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { scheduleKeys } from "@/features/events/api/query-keys";

const gateway = httpEventsGateway;

export function useSchedule(slug: string, fromIso: string, toIso: string) {
  return useQuery({
    queryKey: scheduleKeys.community(slug, fromIso, toIso),
    queryFn: () => gateway.getSchedule(slug, fromIso, toIso),
    placeholderData: keepPreviousData,
  });
}
