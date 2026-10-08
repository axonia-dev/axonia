import { useQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import type { ExpertiseSearchParams } from "@/features/expertise/domain/expertise-gateway";

const gateway = httpExpertiseGateway;

export function useExpertiseSearch(
  slug: string,
  params: ExpertiseSearchParams,
) {
  return useQuery({
    queryKey: expertiseKeys.search(slug, params),
    queryFn: () => gateway.search(slug, params),
    enabled: params.term !== "",
  });
}
