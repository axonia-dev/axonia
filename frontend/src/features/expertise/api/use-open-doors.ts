import { useQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpExpertiseGateway;

/** Under the community's key, so a declaration, a removal or a pause refreshes the shelf. */
export function useOpenDoors(slug: string) {
  return useQuery({
    queryKey: expertiseKeys.openDoors(slug),
    queryFn: () => gateway.openDoors(slug),
  });
}
