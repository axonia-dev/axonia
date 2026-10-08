import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import type { ManagementPolicy } from "@/features/community/domain/management-policy";

const gateway = httpCommunityGateway;

export function useManagementPolicyQuery(slug: string) {
  return useQuery({
    queryKey: communityKeys.managementPolicy(slug),
    queryFn: () => gateway.getManagementPolicy(slug),
  });
}

export function useUpdateManagementPolicy(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (policy: ManagementPolicy) =>
      gateway.updateManagementPolicy(slug, policy),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communityKeys.managementPolicy(slug),
      });
    },
  });
}
