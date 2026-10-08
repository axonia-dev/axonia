import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import type { ChatDestinationInput } from "@/features/community/domain/chat-destination";

const gateway = httpCommunityGateway;

interface QueryOptions {
  enabled?: boolean;
}

/**
 * `enabled` defaults to `true` for the settings page, which is already gated on
 * `access.communication`. Callers reachable by users who may lack that axis -
 * `ChatChannelWarning` on the community's own home - must pass their own access
 * through: a broken destination's name and last error are leadership-only.
 */
export function useChatDestinationsQuery(
  slug: string,
  options: Readonly<QueryOptions> = {},
) {
  return useQuery({
    queryKey: communityKeys.chatDestinations(slug),
    queryFn: () => gateway.listChatDestinations(slug),
    enabled: options.enabled ?? true,
  });
}

function useInvalidate(slug: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: communityKeys.chatDestinations(slug),
    });
  };
}

export function useCreateChatDestination(slug: string) {
  const invalidate = useInvalidate(slug);
  return useMutation({
    mutationFn: (input: ChatDestinationInput) =>
      gateway.createChatDestination(slug, input),
    onSuccess: invalidate,
  });
}

export function useUpdateChatDestination(slug: string) {
  const invalidate = useInvalidate(slug);
  return useMutation({
    mutationFn: (variables: { id: number; input: ChatDestinationInput }) =>
      gateway.updateChatDestination(slug, variables.id, variables.input),
    onSuccess: invalidate,
  });
}

export function useRemoveChatDestination(slug: string) {
  const invalidate = useInvalidate(slug);
  return useMutation({
    mutationFn: (id: number) => gateway.removeChatDestination(slug, id),
    onSuccess: invalidate,
  });
}

/**
 * A test post records health on both success and failure - it either resets the
 * broken state or sets it - so the list is invalidated on either outcome.
 */
export function useTestChatDestination(slug: string) {
  const invalidate = useInvalidate(slug);
  return useMutation({
    mutationFn: (id: number) => gateway.testChatDestination(slug, id),
    onSettled: invalidate,
  });
}
