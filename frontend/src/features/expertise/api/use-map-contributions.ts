import { useQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpExpertiseGateway;

/**
 * The map's contributions, fetched only once the map is the view on screen: this payload is three orders of magnitude
 * larger than the topic map and no other reading needs it.
 */
export function useMapContributions(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: expertiseKeys.mapContributions(slug),
    queryFn: () => gateway.mapContributions(slug),
    enabled,
  });
}
