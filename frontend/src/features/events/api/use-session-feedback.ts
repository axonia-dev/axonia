import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useIsDelegated } from "@/auth/delegated-session";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys } from "@/features/events/api/query-keys";
import type { Session } from "@/features/events/domain/session";
import type { FeedbackAnswer } from "@/features/events/domain/session-feedback";
import { feedbackSectionShows } from "@/features/events/domain/session-feedback";
import { sessionPhase } from "@/features/events/domain/session-phase";
import { healthKeys } from "@/features/health/api/query-keys";

const gateway = httpEventsGateway;

export function useSessionFeedback(sessionId: number, enabled = true) {
  return useQuery({
    queryKey: sessionKeys.feedback(sessionId),
    queryFn: () => gateway.getSessionFeedback(sessionId),
    enabled,
  });
}

/** Whether a session's feedback section has anything for this reader: never before it is held. */
export function useFeedbackSectionShows(session: Session | null): boolean {
  const held = session !== null && sessionPhase(session) === "held";
  const { data } = useSessionFeedback(session?.id ?? 0, held);
  const delegated = useIsDelegated();
  return held && !delegated && feedbackSectionShows(data);
}

/** The response is the whole new feedback state, and the community's health reads it too. */
export function useGiveSessionFeedback(slug: string, sessionId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (answer: FeedbackAnswer) =>
      gateway.giveSessionFeedback(sessionId, answer),
    onSuccess: (feedback) => {
      queryClient.setQueryData(sessionKeys.feedback(sessionId), feedback);
      return queryClient.invalidateQueries({
        queryKey: [...healthKeys.all, slug],
      });
    },
  });
}
