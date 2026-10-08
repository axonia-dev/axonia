import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useRescheduleSession } from "@/features/events/api/use-sessions";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import {
  draftFromPlace,
  placeFromDraft,
} from "@/features/events/domain/meeting-place";
import {
  sameScheduleAs,
  targetFromSession,
} from "@/features/events/domain/reschedule-target";
import type { Session } from "@/features/events/domain/session";
import { MeetingPlaceFields } from "@/features/events/ui/MeetingPlaceFields";
import {
  SectionTypesGate,
  type SectionTypesRead,
} from "@/features/events/ui/SectionTypesGate";
import { SessionStructureEditor } from "@/features/events/ui/SessionStructureEditor";

/**
 * Whether this session's place is editable here rather than in the reschedule dialog. True only for
 * a one-off: it opens no menu item of its own, so it adds nothing to whether the kebab appears, and
 * it lives inside the structure dialog, which an organizer always reaches because `canManage`
 * implies `canEditStructure` at every call site. A series occurrence keeps a single place editor -
 * two blocks writing the same three columns would disagree about what an empty one means.
 */
export function ownPlaceEditable(
  session: Session,
  isSeriesOccurrence: boolean,
  canManage: boolean,
  hasStructureDialog: boolean,
): boolean {
  if (isSeriesOccurrence || !canManage || !hasStructureDialog) {
    return false;
  }
  return session.status === "SCHEDULED";
}

function samePlace(left: MeetingPlace, right: MeetingPlace): boolean {
  return (
    left.location === right.location &&
    left.joinUrl === right.joinUrl &&
    left.joiningDetails === right.joiningDetails
  );
}

/** A stored place put through the same trim a typed one gets, so a row written before the
 *  validator - one carrying stray whitespace - still reads as untouched rather than as an edit,
 *  and is sent in the shape that validator now accepts. */
function normalized(place: MeetingPlace): MeetingPlace {
  return placeFromDraft(draftFromPlace(place));
}

interface Props {
  slug: string;
  session: Session;
  sectionTypes: SectionTypesRead;
  /** Whether the session's length is this reader's to set. `PUT /sessions/{id}` admits only an
   *  organizer, while shaping the structure is open to a contributor too, so a contributor reads
   *  the budget as the plain number it has always been. */
  canEditLength: boolean;
  showOwnPlace: boolean;
  onDone: () => void;
}

/**
 * The body of the "Edit session" dialog: the structure, and the session's own facts that go with
 * it - how long this one occurrence runs, and where a one-off meets.
 *
 * The sections write on the spot, one endpoint per change. The session facts cannot: they share a
 * single full-replacement `PUT`, so two of them saving separately would race, and whichever went
 * second would post the value it read first straight back over the other. They are drafts, and the
 * footer commits them together in one write. That also makes Done mean the same thing whichever
 * facts this dialog happens to be showing.
 *
 * Every draft is seeded on mount and never resynced, which is what makes an abandoned edit
 * discard: the dialog unmounts its body on close, so reopening reads the session again.
 *
 * A refusal - the server rejects a join link that does not start with http - keeps the dialog open
 * with the typed values and the error on screen. Closing is the signal that the write landed.
 */
export function SessionEditBody({
  slug,
  session,
  sectionTypes,
  canEditLength,
  showOwnPlace,
  onDone,
}: Readonly<Props>) {
  const save = useRescheduleSession(slug);
  const [length, setLength] = useState(session.durationMinutes);
  // A one-off inherits nothing, so its own place and its resolved place are the same value. The
  // override is still what is read, so this never has to know which of the two it is holding - and
  // an occurrence that shows no place editor hands its own override straight back rather than the
  // place it resolved to, which would detach it from its series for good.
  const stored = normalized(session.placeOverride);
  const [place, setPlace] = useState(() => draftFromPlace(stored));
  const typedPlace = showOwnPlace ? placeFromDraft(place) : stored;
  const changed =
    length !== session.durationMinutes || !samePlace(typedPlace, stored);

  function done() {
    if (!changed) {
      onDone();
      return;
    }
    save.mutate(
      {
        sessionId: session.id,
        input: {
          scheduledAt: sameScheduleAs(targetFromSession(session)),
          durationMinutes: length,
          facilitatorUserId: session.facilitatorUserId,
          place: typedPlace,
        },
      },
      { onSuccess: () => onDone() },
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <SectionTypesGate sectionTypes={sectionTypes}>
        {(loaded) => (
          <SessionStructureEditor
            slug={slug}
            session={session}
            sectionTypes={loaded}
            budgetMinutes={length}
            {...(canEditLength ? { onBudgetChange: setLength } : {})}
          />
        )}
      </SectionTypesGate>
      {showOwnPlace && <MeetingPlaceFields value={place} onChange={setPlace} />}
      <FormError error={save.isError} />
      <DialogFooter>
        <Button size="sm" isDisabled={save.isPending} onPress={done}>
          Done
        </Button>
      </DialogFooter>
    </div>
  );
}
