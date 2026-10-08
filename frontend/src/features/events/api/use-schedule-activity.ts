import { useQuery } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { scheduleKeys } from "@/features/events/api/query-keys";

const gateway = httpEventsGateway;

const FIVE_MINUTES = 5 * 60 * 1000;

export function useScheduleActivity(slug: string) {
  return useQuery({
    queryKey: scheduleKeys.activity(slug),
    queryFn: () => gateway.getScheduleActivity(slug),
    staleTime: FIVE_MINUTES,
  });
}
