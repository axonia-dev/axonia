import { FEEDBACK_WORDS } from "@/features/events/domain/feedback-words";

export type FeedbackQuestion = "enjoyed" | "learned";

export const FEEDBACK_SCALE = 10;

export type BarTone = "on" | "peek" | "off";

/** The answer under a pointer at `clientX` across a row of ten bars, or null when the row has no
 *  layout to read. */
export function valueAt(
  clientX: number,
  rect: Readonly<{ left: number; width: number }>,
): number | null {
  if (rect.width <= 0) return null;
  const raw = Math.ceil(((clientX - rect.left) / rect.width) * FEEDBACK_SCALE);
  return Math.max(1, Math.min(FEEDBACK_SCALE, raw));
}

/** How one bar paints: a preview ghosts the bars it reaches past the committed answer. */
export function barTone(
  bar: number,
  value: number | null,
  preview: number | null,
): BarTone {
  if (value !== null && bar <= value && (preview === null || bar <= preview)) {
    return "on";
  }
  if (preview !== null && bar <= preview) return "peek";
  return "off";
}

/** The word an answer to `question` reads as. */
export function feedbackWord(
  question: FeedbackQuestion,
  value: number,
): string {
  const words =
    question === "enjoyed" ? FEEDBACK_WORDS.enjoyed : FEEDBACK_WORDS.learned;
  return words.at(value) ?? "";
}
