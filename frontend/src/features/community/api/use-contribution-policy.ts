import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import type { ContributionPolicy } from "@/features/community/domain/contribution-policy";
import { curated } from "@/features/community/domain/contribution-policy";

const gateway = httpCommunityGateway;

export function useContributionPolicyQuery(slug: string) {
  return useQuery({
    queryKey: communityKeys.contributionPolicy(slug),
    queryFn: () => gateway.getContributionPolicy(slug),
  });
}

/** Live policy with the curated stance as the safe default while loading or on error. */
export function useContributionPolicy(slug: string): ContributionPolicy {
  return useContributionPolicyQuery(slug).data ?? curated();
}

export function useUpdateContributionPolicy(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (policy: ContributionPolicy) =>
      gateway.updateContributionPolicy(slug, policy),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communityKeys.contributionPolicy(slug),
      });
    },
  });
}
