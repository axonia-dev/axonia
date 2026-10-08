import { useQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpExpertiseGateway;

export function useExpertiseMap(slug: string) {
  return useQuery({
    queryKey: expertiseKeys.map(slug),
    queryFn: () => gateway.map(slug),
  });
}
