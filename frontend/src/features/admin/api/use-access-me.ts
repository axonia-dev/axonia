import { useQuery } from "@tanstack/react-query";

import { useIsOrgless } from "@/auth/use-is-orgless";
import { httpAccessGateway } from "@/features/admin/adapters/http-access-gateway";
import { accessKeys } from "@/features/admin/api/query-keys";

const gateway = httpAccessGateway;

/** The caller's roles and permissions in their current organization. A person with no
 *  organization holds none, so nothing is asked. A failed answer is asked again by the next
 *  observer that mounts, so the page a member opens after a blip heals by itself. That cannot
 *  loop: every gate reads `failedToLoad`, which holds through the retry, so none remounts. */
export function useAccessMe() {
  return useQuery({
    queryKey: accessKeys.me(),
    queryFn: () => gateway.getMe(),
    staleTime: Infinity,
    enabled: !useIsOrgless(),
  });
}
