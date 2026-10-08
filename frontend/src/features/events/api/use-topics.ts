import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { topicKeys, topicMutationKeys } from "@/features/events/api/query-keys";
import type {
  TopicInput,
  TopicListParams,
} from "@/features/events/domain/events-gateway";
import type { Topic } from "@/features/events/domain/topic";

const gateway = httpEventsGateway;

export function useTopics(slug: string, params: TopicListParams) {
  return useQuery({
    queryKey: topicKeys.list(slug, params),
    queryFn: () => gateway.listTopics(slug, params),
  });
}

export function useTopicCounts(slug: string) {
  return useQuery({
    queryKey: topicKeys.counts(slug),
    queryFn: () => gateway.getTopicCounts(slug),
  });
}

export function usePastTopics(
  slug: string,
  params: { page: number; size: number; q?: string },
) {
  const listParams: TopicListParams = { ...params, phase: "PAST" };
  return useQuery({
    queryKey: topicKeys.list(slug, listParams),
    queryFn: () => gateway.listTopics(slug, listParams),
  });
}

export function useProposeTopic(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: TopicInput) => gateway.proposeTopic(slug, input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: topicKeys.community(slug) }),
  });
}

export function useVoteTopic(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: topicMutationKeys.vote,
    mutationFn: (topicId: number) => gateway.voteTopic(topicId),
    onMutate: async (topicId) => {
      await queryClient.cancelQueries({ queryKey: topicKeys.community(slug) });
      const prev = queryClient.getQueriesData({
        queryKey: topicKeys.community(slug),
      });
      queryClient.setQueriesData(
        { queryKey: topicKeys.community(slug) },
        (old: unknown) =>
          patchTopicInPage(old, topicId, (t) => ({
            ...t,
            voteCount: t.voteCount + 1,
            hasVoted: true,
          })),
      );
      return { prev };
    },
    onError: (_err, _topicId, ctx) => {
      for (const [key, data] of ctx?.prev ?? []) {
        queryClient.setQueryData(key, data);
      }
    },
    // Reconcile in the background. Do NOT return the promise: returning it
    // keeps the mutation in `pending` until the refetch resolves, which —
    // because the vote button's disabled state is derived from the mutation
    // cache — would re-disable the button during (and stall it on) the refetch.
    onSettled: () => {
      void queryClient.invalidateQueries({
        queryKey: topicKeys.community(slug),
      });
    },
  });
}

export function useUnvoteTopic(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: topicMutationKeys.unvote,
    mutationFn: (topicId: number) => gateway.unvoteTopic(topicId),
    onMutate: async (topicId) => {
      await queryClient.cancelQueries({ queryKey: topicKeys.community(slug) });
      const prev = queryClient.getQueriesData({
        queryKey: topicKeys.community(slug),
      });
      queryClient.setQueriesData(
        { queryKey: topicKeys.community(slug) },
        (old: unknown) =>
          patchTopicInPage(old, topicId, (t) => ({
            ...t,
            voteCount: Math.max(0, t.voteCount - 1),
            hasVoted: false,
          })),
      );
      return { prev };
    },
    onError: (_err, _topicId, ctx) => {
      for (const [key, data] of ctx?.prev ?? []) {
        queryClient.setQueryData(key, data);
      }
    },
    // See useVoteTopic: background reconcile only — never block the mutation
    // (and the button) on the refetch.
    onSettled: () => {
      void queryClient.invalidateQueries({
        queryKey: topicKeys.community(slug),
      });
    },
  });
}

export function useEditTopic(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ topicId, input }: { topicId: number; input: TopicInput }) =>
      gateway.editTopic(topicId, input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: topicKeys.community(slug) }),
  });
}

export function useDeleteTopic(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (topicId: number) => gateway.deleteTopic(topicId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: topicKeys.community(slug) }),
  });
}

// Utility: patch a single topic inside a TanStack Query page result
function patchTopicInPage(
  old: unknown,
  topicId: number,
  patch: (t: Topic) => Topic,
): unknown {
  if (!old || typeof old !== "object" || !("content" in old)) return old;
  const page = old as { content: Topic[] };
  return {
    ...page,
    content: page.content.map((t) => (t.id === topicId ? patch(t) : t)),
  };
}
