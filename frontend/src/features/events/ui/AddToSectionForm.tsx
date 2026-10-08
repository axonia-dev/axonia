import { useState } from "react";

import { Button } from "@/components/ui/Button";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { SessionMode } from "@/features/events/ui/session-mode";
import { SlotDialog } from "@/features/events/ui/SlotDialog";

interface Props {
  slug: string;
  session: Session;
  section: Section;
  canQueueTopics?: boolean;
  mode?: SessionMode;
}

/**
 * The "+ Add a slot" affordance: a ghost button that opens the shared SlotDialog.
 *
 * In edit mode - a held session an organizer opened to correct or backfill it - this must record
 * who actually presented, never sign the caller up: it opens the dialog in "record" mode instead
 * of "add", which also drops the backlog source (pulling a proposed future topic into a past
 * session is wrong).
 */
export function AddToSectionForm({
  slug,
  session,
  section,
  canQueueTopics = false,
  mode = "contribute",
}: Readonly<Props>) {
  const [open, setOpen] = useState(false);
  const recording = mode === "edit";
  return (
    <>
      <Button
        variant="ghost"
        size="sm"
        className="self-start"
        onPress={() => setOpen(true)}
      >
        + Add a slot
      </Button>
      {recording ? (
        <SlotDialog
          mode="record"
          slug={slug}
          session={session}
          section={section}
          isOpen={open}
          onOpenChange={setOpen}
        />
      ) : (
        <SlotDialog
          mode="add"
          slug={slug}
          session={session}
          section={section}
          canQueueTopics={canQueueTopics}
          isOpen={open}
          onOpenChange={setOpen}
        />
      )}
    </>
  );
}
