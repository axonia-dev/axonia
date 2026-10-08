import { useQuery } from "@tanstack/react-query";

import { httpHealthGateway } from "@/features/health/adapters/http-health-gateway";
import { healthKeys } from "@/features/health/api/query-keys";

const gateway = httpHealthGateway;

export function useHealthSnapshot(slug: string, window: string) {
  return useQuery({
    queryKey: healthKeys.snapshot(slug, window),
    queryFn: () => gateway.getSnapshot(slug, window),
  });
}
