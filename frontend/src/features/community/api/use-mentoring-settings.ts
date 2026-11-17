import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";

const gateway = httpCommunityGateway;

export function useMentoringSettings(slug: string) {
  return useQuery({
    queryKey: communityKeys.mentoringSettings(slug),
    queryFn: () => gateway.getMentoringSettings(slug),
  });
}

/** The community detail carries `mentoringEnabled` too, so a switch refreshes both, and the mentoring caches that follow the switch. */
export function useUpdateMentoringSettings(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) =>
      gateway.updateMentoringSettings(slug, { enabled }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: communityKeys.detail(slug) }),
        queryClient.invalidateQueries({ queryKey: mentoringKeys.all }),
      ]),
  });
}
