import type { StatusTone } from "@/components/ui/StatusChip";
import type {
  ReleaseState,
  ReleaseStatus,
  Session,
} from "@/features/events/domain/session";
import type { SessionPhase } from "@/features/events/domain/session-phase";
import type { ReleaseCopy } from "@/features/events/ui/release-copy";
import { upcomingCopy, wrapUpCopy } from "@/features/events/ui/release-copy";
import {
  REMINDER_PREVIEW,
  WRAP_UP_PREVIEW,
} from "@/features/events/ui/release-preview-copy";
import type { ReleasePreviewCopy } from "@/features/events/ui/ReleasePreviewDialog";

/** The noun a message type goes by, capitalised for the head of a chip. */
type Noun = "Wrap-up" | "Reminder";

export interface ReleaseChip {
  tone: StatusTone;
  label: string;
  /** Whether previewing it is worth offering. A message nothing will ever send is not. */
  previewable: boolean;
}

/**
 * A release stated in as few words as a reader can act on, for the one chip the facts strip
 * carries.
 *
 * `needsAttention` is the same signal the card's own copy raises, passed in rather than
 * recomputed: a reminder a reschedule left sitting after the session's own start is still stored
 * as `SCHEDULED`, so a chip reading only the state would call it healthy while the card below it
 * said it would never go out. It is the one case where the state alone is not the truth.
 */
export function releaseChip(
  noun: Noun,
  state: ReleaseState,
  needsAttention: boolean,
): ReleaseChip {
  if (state === "SCHEDULED" && needsAttention) {
    return {
      tone: "warning",
      label: `${noun} needs a time`,
      previewable: true,
    };
  }
  switch (state) {
    case "SENT":
      return { tone: "success", label: `${noun} sent`, previewable: true };
    case "QUEUED":
      return { tone: "warning", label: `${noun} queued`, previewable: true };
    case "SCHEDULED":
      return { tone: "neutral", label: `${noun} scheduled`, previewable: true };
    case "OFF":
      return { tone: "neutral", label: `${noun} off`, previewable: false };
  }
}

/**
 * Whether a state is still worth a reader's attention in this phase.
 *
 * Once a session's start has passed with nobody answering for it, its reminder is decided: either
 * it went out or it never will, and no instant before the start is left to pick. The change menu
 * that would offer one is gone with it, so an unsettled reminder stated there would be a fact
 * nobody can act on, and a stranded one a warning with no remedy - the exact state the release
 * card was built to abolish. Every other phase states its release whatever the state.
 */
function stillWorthStating(state: ReleaseState, phase: SessionPhase): boolean {
  if (phase !== "awaiting") return true;
  return state === "SENT" || state === "QUEUED";
}

/** Which message type a read landed on, so a caller can offer the answers that post its own
 *  payload without re-deriving the phase rule `sessionRelease` has already applied. */
type ReleaseKind = "reminder" | "wrapUp";

/**
 * Whether the release is still open to an answer.
 *
 * Deliberately not the chip's `previewable`: the two disagree in both directions. A suppressed
 * message takes every answer and has nothing to preview; a sent one is the exact reverse.
 */
function answerable(state: ReleaseState): boolean {
  return state === "OFF" || state === "SCHEDULED";
}

export interface SessionRelease {
  status: ReleaseStatus;
  chip: ReleaseChip;
  preview: ReleasePreviewCopy;
  /** The sentence and its subline, for the panel the chip opens. The chip's own label is as
   *  short as the strip can carry; this is the full fact, stated once, where it is acted on. */
  copy: ReleaseCopy;
  kind: ReleaseKind;
  answerable: boolean;
}

/**
 * The one release a session's own phase puts in play, or none.
 *
 * A session still ahead of us is answerable for its reminder; a held one for its wrap-up. Only
 * ever one, because the other has either not begun to have a state or has finished having one,
 * and a strip that stated both would spend a reader's attention on a message nothing can be done
 * about. A cancelled or skipped session is answerable for neither.
 *
 * A `null` status means "this is not the detail read", never a state, so it yields no chip rather
 * than a state invented from the absence - the same rule `ReleaseCard` follows.
 */
export function sessionRelease(
  session: Session,
  phase: SessionPhase,
): SessionRelease | null {
  if (phase === "closed") {
    return null;
  }
  const held = phase === "held";
  const status = held ? session.wrapUpStatus : session.upcomingStatus;
  if (status === null || !stillWorthStating(status.state, phase)) {
    return null;
  }
  const copy = held
    ? wrapUpCopy(session, status)
    : upcomingCopy(session, status);
  return {
    status,
    chip: releaseChip(
      held ? "Wrap-up" : "Reminder",
      status.state,
      copy.needsAttention === true,
    ),
    preview: held ? WRAP_UP_PREVIEW : REMINDER_PREVIEW,
    copy,
    kind: held ? "wrapUp" : "reminder",
    answerable: answerable(status.state),
  };
}
