import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import type { CommunicationSettings } from "@/features/community/domain/communication-settings";

const gateway = httpCommunityGateway;

export function useCommunicationSettingsQuery(slug: string) {
  return useQuery({
    queryKey: communityKeys.communicationSettings(slug),
    queryFn: () => gateway.getCommunicationSettings(slug),
  });
}

export function useUpdateCommunicationSettings(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: CommunicationSettings) =>
      gateway.updateCommunicationSettings(slug, settings),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communityKeys.communicationSettings(slug),
      });
    },
  });
}
