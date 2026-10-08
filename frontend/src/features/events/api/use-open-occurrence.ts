import { useNavigate } from "react-router-dom";

import { useEnsureOccurrence } from "@/features/events/api/use-sessions";

/**
 * Materializes a ghost occurrence and navigates to the now-real session page so
 * the member lands on the event they just opened for sign-up.
 */
export function useOpenOccurrence(slug: string) {
  const navigate = useNavigate();
  const ensure = useEnsureOccurrence(slug);

  function open(seriesId: number, occurrenceDate: string): void {
    ensure.mutate(
      { seriesId, occurrenceDate },
      {
        onSuccess: (session) => {
          void navigate(`/communities/${slug}/sessions/${session.id}`);
        },
      },
    );
  }

  return { open, isPending: ensure.isPending };
}
