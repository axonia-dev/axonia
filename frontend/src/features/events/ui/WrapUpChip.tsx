import { notifyError } from "@/components/ui/toast";
import { useSetWrapUpRelease } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import { eventsErrorMessage } from "@/features/events/events-errors";
import type {
  ReleaseActions,
  ReleaseMenuCopy,
} from "@/features/events/ui/release-answers";
import type { SessionRelease } from "@/features/events/ui/release-chip";
import { SessionReleaseChip } from "@/features/events/ui/SessionReleaseChip";

const COPY: ReleaseMenuCopy = {
  note: "When does the wrap-up go out",
  offHint: "This session gets no wrap-up",
  dialogTitle: "Schedule the wrap-up",
  fieldLabel: "Wrap-up release date and time",
};

/**
 * The wrap-up's three answers, as the single `PUT .../wrap-up-release` that applies all of
 * them. "Send it now" posts the browser's own clock, which is safe only because
 * `SessionReleaseService.setWrapUpRelease` clamps a past instant up to the server's before storing
 * it. The reminder refuses that clamp, so the reminder must not copy
 * this: the asymmetry between the two payloads is deliberate.
 */
function useWrapUpReleaseActions(
  slug: string,
  sessionId: number,
): ReleaseActions {
  const release = useSetWrapUpRelease(slug);
  const onError = (error: unknown) => notifyError(eventsErrorMessage(error));

  return {
    isPending: release.isPending,
    reset: () => release.reset(),
    sendNow: () =>
      release.mutate(
        { sessionId, input: { releaseAt: new Date().toISOString() } },
        { onError },
      ),
    scheduleAt: (releaseAt, onScheduled) =>
      release.mutate(
        { sessionId, input: { releaseAt } },
        { onSuccess: onScheduled, onError },
      ),
    suppress: () =>
      release.mutate({ sessionId, input: { releaseAt: null } }, { onError }),
  };
}

/** The wrap-up's instance of the shared release chip: its words and its mutations. It has no
 *  upper bound on when it may go out, so it passes none. */
export function WrapUpChip({
  slug,
  session,
  release,
}: Readonly<{ slug: string; session: Session; release: SessionRelease }>) {
  const actions = useWrapUpReleaseActions(slug, session.id);

  return (
    <SessionReleaseChip
      slug={slug}
      sessionId={session.id}
      release={release}
      answers={{ releaseAt: session.wrapUpReleaseAt, copy: COPY, actions }}
    />
  );
}
