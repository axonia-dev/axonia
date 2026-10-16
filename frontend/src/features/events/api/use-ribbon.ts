import { useQuery } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { scheduleKeys } from "@/features/events/api/query-keys";

const gateway = httpEventsGateway;

export function useRibbon(slug: string, fromIso: string, toIso: string) {
  return useQuery({
    queryKey: scheduleKeys.ribbon(slug, fromIso, toIso),
    queryFn: () => gateway.getRibbon(slug, fromIso, toIso),
  });
}
