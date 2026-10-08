import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpOrganizationsGateway;

/** The organization-wide kill switch mutation: the response is the full,
 *  freshly-read Organization, so the current-organization cache is written
 *  straight from it rather than merely invalidated - the switch's own next
 *  render must reflect the new state without a second round trip. */
export function useUpdateCommunicationsEnabled() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => gateway.setCommunicationsEnabled(enabled),
    onSuccess: (organization) => {
      queryClient.setQueryData(adminKeys.currentOrganization(), organization);
    },
  });
}
