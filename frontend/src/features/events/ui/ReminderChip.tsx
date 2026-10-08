import { notifyError } from "@/components/ui/toast";
import { useSetUpcomingRelease } from "@/features/events/api/use-session";
import type { UpcomingReleaseInput } from "@/features/events/domain/events-gateway";
import type { Session } from "@/features/events/domain/session";
import { eventsErrorMessage } from "@/features/events/events-errors";
import type {
  ReleaseActions,
  ReleaseMenuCopy,
} from "@/features/events/ui/release-answers";
import type { SessionRelease } from "@/features/events/ui/release-chip";
import { SessionReleaseChip } from "@/features/events/ui/SessionReleaseChip";

const COPY: ReleaseMenuCopy = {
  note: "When does the reminder go out",
  offHint: "This session gets no reminder",
  dialogTitle: "Schedule the reminder",
  fieldLabel: "Reminder release date and time",
};

/**
 * The reminder's three answers, as the single `PUT .../upcoming-release` that applies all of
 * them. Each is one of the three mutually exclusive payloads `UpcomingReleaseRequest` takes,
 * and the send-now one is deliberately not the wrap-up's: `{sendNow: true}` lets the server
 * stamp its own clock, where the wrap-up posts the browser's and relies on a clamp the
 * reminder refuses. A browser running slow would otherwise have its
 * "Send it now" rejected as a pick in the past.
 */
function useUpcomingReleaseActions(
  slug: string,
  sessionId: number,
): ReleaseActions {
  const release = useSetUpcomingRelease(slug);
  const answer = (input: UpcomingReleaseInput, onSuccess?: () => void) =>
    release.mutate(
      { sessionId, input },
      {
        onError: (error: unknown) => notifyError(eventsErrorMessage(error)),
        ...(onSuccess === undefined ? {} : { onSuccess }),
      },
    );

  return {
    isPending: release.isPending,
    reset: () => release.reset(),
    sendNow: () => answer({ sendNow: true }),
    scheduleAt: (releaseAt, onScheduled) => answer({ releaseAt }, onScheduled),
    // The empty payload is how this endpoint spells "no reminder": neither answer given.
    suppress: () => answer({}),
  };
}

/** The reminder's instance of the shared release chip: its words, its mutations, and its
 *  bound - the session's own start, past which a reminder is not a reminder. */
export function ReminderChip({
  slug,
  session,
  release,
}: Readonly<{ slug: string; session: Session; release: SessionRelease }>) {
  const actions = useUpcomingReleaseActions(slug, session.id);

  return (
    <SessionReleaseChip
      slug={slug}
      sessionId={session.id}
      release={release}
      answers={{
        releaseAt: session.upcomingReleaseAt,
        copy: COPY,
        actions,
        latestReleaseAt: session.scheduledAt,
      }}
    />
  );
}
