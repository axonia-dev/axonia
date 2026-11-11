import { useIsDelegated } from "@/auth/delegated-session";
import { useSessionFeedback } from "@/features/events/api/use-session-feedback";
import type { Session } from "@/features/events/domain/session";
import {
  FEEDBACK_ANCHOR_ID,
  feedbackSectionShows,
  shownResult,
} from "@/features/events/domain/session-feedback";
import { FeedbackResult } from "@/features/events/ui/FeedbackResult";
import { SessionFeedbackCard } from "@/features/events/ui/SessionFeedbackCard";
import { useFragmentScroll } from "@/features/events/ui/use-fragment-scroll";

/**
 * The ask for anyone who may answer, presenters included, brought into view when a link names it.
 * A delegated session shows none, because only the person's own sign-in may answer.
 */
export function SessionFeedback({
  slug,
  session,
}: Readonly<{ slug: string; session: Session }>) {
  const { data: feedback } = useSessionFeedback(session.id);
  const delegated = useIsDelegated();
  const asks = !delegated && feedbackSectionShows(feedback);
  useFragmentScroll(FEEDBACK_ANCHOR_ID, asks);
  if (!asks || !feedback) return null;
  return (
    <SessionFeedbackCard
      slug={slug}
      sessionId={session.id}
      feedback={feedback}
    />
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
