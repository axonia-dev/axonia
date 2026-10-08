import { useQuery } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { memberZoneKeys } from "@/features/events/api/query-keys";

/**
 * The zones a community spans.
 *
 * It carries no candidate time, so an organizer moving the hour in a form costs no request: the
 * projection into each zone happens locally, on data already held. A roster's zones barely move,
 * so the answer stays fresh for a while rather than being re-asked on every field focus.
 */
export function useMemberZones(slug: string) {
  return useQuery({
    queryKey: memberZoneKeys.community(slug),
    queryFn: () => httpEventsGateway.memberZones(slug),
    staleTime: 5 * 60 * 1000,
  });
}
