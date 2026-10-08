import { useQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpExpertiseGateway;

export function useExpertiseAreas(slug: string, search?: string) {
  return useQuery({
    queryKey: expertiseKeys.areas(slug, search),
    queryFn: () => gateway.areas(slug, search),
  });
}
