import { z } from "zod";

const feedbackAnswerSchema = z.object({
  enjoyed: z.number(),
  learned: z.number(),
});

export type FeedbackAnswer = z.infer<typeof feedbackAnswerSchema>;

const feedbackResultSchema = z.object({
  state: z.enum(["PENDING", "TOO_FEW", "REVEALED"]),
  answers: z.number(),
  enjoyed: z.number().nullable(),
  learned: z.number().nullable(),
  importedFrom: z.string().nullable(),
});

export type FeedbackResult = z.infer<typeof feedbackResultSchema>;

export const sessionFeedbackSchema = z.object({
  window: z.enum(["OPEN", "CLOSED", "NONE"]),
  closesAt: z.string().nullable(),
  role: z.enum(["ATTENDEE", "PRESENTER", "OTHER"]),
  mine: feedbackAnswerSchema.nullable(),
  result: feedbackResultSchema.nullable(),
});

export type SessionFeedback = z.infer<typeof sessionFeedbackSchema>;

/** An attendee is asked while the window is open, and keeps the receipt of an answer once it closes. */
export function asksAttendee(feedback: SessionFeedback): boolean {
  if (feedback.role !== "ATTENDEE") return false;
  return feedback.window === "OPEN" || feedback.mine !== null;
}

export function tellsPresenter(feedback: SessionFeedback): boolean {
  return feedback.role === "PRESENTER" && feedback.window === "OPEN";
}

/** Whether the session's feedback section has anything for this reader. */
export function feedbackSectionShows(
  feedback: SessionFeedback | undefined,
): boolean {
  if (!feedback) return false;
  return asksAttendee(feedback) || tellsPresenter(feedback);
}

/** How the session landed, once there is something to show: never while it is still pending. */
export function shownResult(
  feedback: SessionFeedback | undefined,
): FeedbackResult | null {
  const result = feedback?.result ?? null;
  return result?.state === "PENDING" ? null : result;
}

/** The session page's feedback section, which a wrap-up mail links to as `#feedback`. */
export const FEEDBACK_ANCHOR_ID = "feedback";
