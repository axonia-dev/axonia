import { useQueryClient } from "@tanstack/react-query";

import { sessionKeys, topicKeys } from "@/features/events/api/query-keys";

/**
 * A mutation that changes a session's structure (slots, sections, presenters) invalidates both
 * the session detail and the community's session list, since list rows and the schedule feed can
 * also carry structural summaries. It invalidates the community's topics too: a scheduled topic's
 * speakers, and so its kind, are read from its slot's presenters.
 */
export function useInvalidateSession(slug: string, sessionId: number) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: sessionKeys.community(slug),
    });
    void queryClient.invalidateQueries({
      queryKey: sessionKeys.detail(sessionId),
    });
    void queryClient.invalidateQueries({
      queryKey: topicKeys.community(slug),
    });
  };
}
