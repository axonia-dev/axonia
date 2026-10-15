import { useQuery } from "@tanstack/react-query";

import { httpHealthGateway } from "@/features/health/adapters/http-health-gateway";
import { healthKeys } from "@/features/health/api/query-keys";

const FIVE_MINUTES = 5 * 60 * 1000;

export function useActivityTrace(slug: string) {
  return useQuery({
    queryKey: healthKeys.activityTrace(slug),
    queryFn: () => httpHealthGateway.activityTrace(slug),
    staleTime: FIVE_MINUTES,
  });
}
