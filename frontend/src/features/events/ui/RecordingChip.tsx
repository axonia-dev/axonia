import { CirclePlay, CirclePlus } from "lucide-react";
import { useState } from "react";

import { pressableChipClass } from "@/components/ui/StatusChip";
import type { Session } from "@/features/events/domain/session";
import { sessionPhase } from "@/features/events/domain/session-phase";
import { FollowUpDialog } from "@/features/events/ui/FollowUpDialog";

interface Props {
  slug: string;
  session: Session;
  canManage: boolean;
}

/** One tint across both states, so the invitation to add a recording becomes the chip stating one. */
const CHIP = pressableChipClass("primary");

/**
 * Whether the strip has a recording to spend a fact on.
 *
 * The card asks before handing the slot over: each fact is drawn with a leading rule, so a chip
 * that renders nothing still leaves a divider standing against nothing.
 */
export function statesRecording(session: Session, canManage: boolean): boolean {
  return (
    sessionPhase(session) === "held" &&
    (session.recordingUrl !== null || canManage)
  );
}

/**
 * A held session's recording, stated in the facts strip as the fact it is.
 *
 * One word, and one control at most: the chip either links to the recording or asks an organizer
 * for it. Editing what is already there is the actions menu's, beside everything else an organizer
 * does to a session, so this never becomes a link with a pencil beside it.
 */
export function RecordingChip({ slug, session, canManage }: Readonly<Props>) {
  const [open, setOpen] = useState(false);
  if (!statesRecording(session, canManage)) {
    return null;
  }
  if (session.recordingUrl !== null) {
    return (
      <a
        href={session.recordingUrl}
        target="_blank"
        rel="noreferrer"
        className={CHIP}
      >
        <CirclePlay aria-hidden size={13} />
        Recording
      </a>
    );
  }
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={CHIP}>
        <CirclePlus aria-hidden size={13} />
        Add a recording
      </button>
      <FollowUpDialog
        slug={slug}
        sessionId={session.id}
        recordingUrl={session.recordingUrl}
        notes={session.notes}
        isOpen={open}
        onOpenChange={setOpen}
      />
    </>
  );
}
