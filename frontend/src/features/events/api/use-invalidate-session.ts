import { useQueryClient } from "@tanstack/react-query";

import { sessionKeys } from "@/features/events/api/query-keys";

/**
 * A mutation that changes a session's structure (slots, sections, presenters) invalidates both
 * the session detail and the community's session list, since list rows and the schedule feed can
 * also carry structural summaries.
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
  };
}
