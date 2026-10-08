import { useQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpExpertiseGateway;

/** The member's own expertise: across the organization, or inside the community a slug names. */
export function useSelfView(slug?: string) {
  return useQuery({
    queryKey: expertiseKeys.selfView(slug),
    queryFn: () =>
      slug === undefined ? gateway.selfView() : gateway.selfViewIn(slug),
  });
}
