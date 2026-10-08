import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys, topicKeys } from "@/features/events/api/query-keys";
import type {
  QueueTopicInput,
  SlotInput,
} from "@/features/events/domain/slot-gateway";

const gateway = httpEventsGateway;

function useInvalidateSessionsAndTopics(slug: string, sessionId?: number) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: sessionKeys.community(slug),
    });
    void queryClient.invalidateQueries({ queryKey: topicKeys.community(slug) });
    if (sessionId !== undefined) {
      void queryClient.invalidateQueries({
        queryKey: sessionKeys.detail(sessionId),
      });
    }
  };
}

export function usePresentFromBacklog(
  slug: string,
  sessionId: number,
  sectionId: number,
) {
  const invalidate = useInvalidateSessionsAndTopics(slug, sessionId);
  return useMutation({
    mutationFn: (input: SlotInput) =>
      gateway.addSlot(sessionId, sectionId, input),
    onSuccess: invalidate,
  });
}

export function useQueueTopic(
  slug: string,
  sessionId: number,
  sectionId: number,
) {
  const invalidate = useInvalidateSessionsAndTopics(slug, sessionId);
  return useMutation({
    mutationFn: (input: QueueTopicInput) =>
      gateway.queueTopic(sessionId, sectionId, input),
    onSuccess: invalidate,
  });
}

export function useReturnToBacklog(slug: string) {
  const invalidate = useInvalidateSessionsAndTopics(slug);
  return useMutation({
    mutationFn: (topicId: number) => gateway.returnToBacklog(topicId),
    onSuccess: invalidate,
  });
}
