import {
  skipToken,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { adminKeys } from "@/features/admin/api/query-keys";
import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import {
  communityKeys,
  followsCommunityUpdates,
} from "@/features/community/api/query-keys";
import type {
  CommunityInput,
  ListParams,
} from "@/features/community/domain/community-gateway";

const gateway = httpCommunityGateway;

export function useCommunities(params: ListParams) {
  return useQuery({
    queryKey: communityKeys.list(params),
    queryFn: () => gateway.list(params),
  });
}

/** No slug reads nothing, for a view shown both inside and outside a community. */
export function useCommunity(slug: string | undefined) {
  return useQuery({
    queryKey: communityKeys.detail(slug ?? ""),
    queryFn: slug === undefined ? skipToken : () => gateway.getBySlug(slug),
  });
}

export function useCreateCommunity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CommunityInput) => gateway.create(input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: communityKeys.lists() }),
  });
}

export function useUpdateCommunity(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CommunityInput) => gateway.update(slug, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communityKeys.detail(slug),
      });
      void queryClient.invalidateQueries({ queryKey: communityKeys.lists() });
      // In a hub the community's name and description feed the organization's public card,
      // the switcher label and discovery: refresh those reads too.
      void queryClient.invalidateQueries({
        queryKey: adminKeys.myOrganizations(),
      });
      void queryClient.invalidateQueries({
        queryKey: adminKeys.currentOrganization(),
      });
      void queryClient.invalidateQueries({
        predicate: followsCommunityUpdates,
      });
    },
  });
}

export function useArchiveCommunity(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.archive(slug),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: communityKeys.detail(slug),
      });
      void queryClient.invalidateQueries({ queryKey: communityKeys.lists() });
    },
  });
}
