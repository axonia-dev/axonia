import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { topicKeys } from "@/features/events/api/query-keys";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpEventsGateway;

/** Refreshes on a refusal too: a topic scheduled from another tab answers NOT_PROPOSED, and the
 *  backlog should then drop it. An offer counts as its speakers' expertise, so that moves too. */
function useRefreshTopics(slug: string) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: topicKeys.community(slug) }),
      queryClient.invalidateQueries({
        queryKey: expertiseKeys.community(slug),
      }),
    ]);
}

export function useAddSelfAsSpeaker(slug: string, topicId: number) {
  const refresh = useRefreshTopics(slug);
  return useMutation({
    mutationFn: () => gateway.addSelfAsSpeaker(topicId),
    onSettled: refresh,
  });
}

export function useAddSpeaker(slug: string, topicId: number) {
  const refresh = useRefreshTopics(slug);
  return useMutation({
    mutationFn: (speaker: string) => gateway.addSpeaker(topicId, speaker),
    onSettled: refresh,
  });
}

export function useWithdrawAsSpeaker(slug: string, topicId: number) {
  const refresh = useRefreshTopics(slug);
  return useMutation({
    mutationFn: () => gateway.withdrawAsSpeaker(topicId),
    onSettled: refresh,
  });
}

export function useRemoveSpeaker(slug: string, topicId: number) {
  const refresh = useRefreshTopics(slug);
  return useMutation({
    mutationFn: (userId: string) => gateway.removeSpeaker(topicId, userId),
    onSettled: refresh,
  });
}
