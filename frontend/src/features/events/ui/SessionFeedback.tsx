import { useIsDelegated } from "@/auth/delegated-session";
import { useSessionFeedback } from "@/features/events/api/use-session-feedback";
import type { Session } from "@/features/events/domain/session";
import {
  asksAttendee,
  FEEDBACK_ANCHOR_ID,
  feedbackSectionShows,
  shownResult,
  tellsPresenter,
} from "@/features/events/domain/session-feedback";
import { FeedbackPresenterLine } from "@/features/events/ui/FeedbackPresenterLine";
import { FeedbackResult } from "@/features/events/ui/FeedbackResult";
import { SessionFeedbackCard } from "@/features/events/ui/SessionFeedbackCard";
import { useFragmentScroll } from "@/features/events/ui/use-fragment-scroll";

/**
 * The ask for an attendee and a quiet line for a speaker, brought into view when a link names it.
 * A delegated session shows neither, because only the person's own sign-in may answer.
 */
export function SessionFeedback({
  slug,
  session,
}: Readonly<{ slug: string; session: Session }>) {
  const { data: feedback } = useSessionFeedback(session.id);
  const delegated = useIsDelegated();
  useFragmentScroll(
    FEEDBACK_ANCHOR_ID,
    !delegated && feedbackSectionShows(feedback),
  );
  if (!feedback || delegated) return null;
  const card = asksAttendee(feedback);
  const line = tellsPresenter(feedback);
  if (!card && !line) return null;
  return (
    <div className="flex flex-col gap-3">
      {card && (
        <SessionFeedbackCard
          slug={slug}
          sessionId={session.id}
          feedback={feedback}
        />
      )}
      {line && <FeedbackPresenterLine closesAt={feedback.closesAt} />}
    </div>
  );
}

/** How the session landed, for whoever runs it, once there is something to show. */
export function SessionFeedbackResult({
  session,
}: Readonly<{ session: Session }>) {
  const { data: feedback } = useSessionFeedback(session.id);
  const result = shownResult(feedback);
  return result && <FeedbackResult result={result} />;
}
