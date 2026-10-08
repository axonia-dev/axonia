import { CalendarClock, CirclePlay, Pencil, SkipForward } from "lucide-react";
import { useState } from "react";
import {
  type Key,
  Menu as AriaMenu,
  MenuTrigger,
  Popover,
} from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { MenuItem, menuPopoverClass } from "@/components/ui/Menu";
import { useSectionTypes } from "@/features/events/api/use-section-types";
import {
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
import { targetFromSession } from "@/features/events/domain/reschedule-target";
import type { Session } from "@/features/events/domain/session";
import { hasPassed } from "@/features/events/domain/session";
import { sessionPhase } from "@/features/events/domain/session-phase";
import { FollowUpDialog } from "@/features/events/ui/FollowUpDialog";
import { RescheduleDialog } from "@/features/events/ui/RescheduleDialog";
import {
  ownPlaceEditable,
  SessionEditBody,
} from "@/features/events/ui/SessionEditBody";

interface Props {
  slug: string;
  session: Session;
  canEditStructure: boolean;
  canManage: boolean;
  /** Whether a held session has been explicitly opened for editing. Ignored for a scheduled
   * session, whose structure editor stays reachable exactly as it always has. */
  editing?: boolean;
}

/**
 * Skipping is for calling an occurrence off in advance. The server refuses a retroactive one
 * outright (`OCCURRENCE_IN_PAST`, whose own message says to cancel it instead), and nothing on this
 * page surfaces that conflict, so an item offered past the hour read as live and answered by doing
 * nothing at all. Reschedule stays: moving a session nobody held is exactly what it is for.
 */
function skippable(
  session: Session,
  isScheduledSeriesOccurrence: boolean,
): boolean {
  return isScheduledSeriesOccurrence && !hasPassed(session);
}

function isSeriesOccurrence(session: Session): boolean {
  return (
    session.status === "SCHEDULED" &&
    session.seriesId !== null &&
    session.occurrenceDate !== null
  );
}

/**
 * A scheduled session's structure stays reachable exactly as it always has. A held session's
 * structure is the same operation the domain gate used to guard - it is reachable only once the
 * Edit toggle is on, matching the affordance that toggle was introduced to gate. Cancelled and
 * skipped sessions stay closed either way.
 */
function structureReachable(session: Session, editing: boolean): boolean {
  return (
    session.status === "SCHEDULED" || (session.status === "HELD" && editing)
  );
}

interface ActionVisibility {
  showEditStructure: boolean;
  showReschedule: boolean;
  showSkip: boolean;
  showOwnPlace: boolean;
  showFollowUp: boolean;
  hasActions: boolean;
}

function computeActions(
  session: Session,
  canEditStructure: boolean,
  canManage: boolean,
  editing: boolean,
): ActionVisibility {
  const occurrence = isSeriesOccurrence(session);
  const showEditStructure =
    canEditStructure && structureReachable(session, editing);
  const showReschedule = canManage && occurrence;
  const showSkip = canManage && skippable(session, occurrence);
  const showOwnPlace = ownPlaceEditable(
    session,
    occurrence,
    canManage,
    showEditStructure,
  );
  // The recording and the note are the record of a session that ran, so they are offered only
  // once it has - and here rather than beside the note itself, so a held session carries one
  // control for its record instead of a link and a pencil side by side.
  const showFollowUp = canManage && sessionPhase(session) === "held";
  return {
    showEditStructure,
    showReschedule,
    showSkip,
    showOwnPlace,
    showFollowUp,
    hasActions: showEditStructure || showReschedule || showSkip || showFollowUp,
  };
}

interface Handlers {
  onEditOpen: () => void;
  onRescheduleOpen: () => void;
  onFollowUpOpen: () => void;
  onSkip: (args: { seriesId: number; occurrenceDate: string }) => void;
}

function createActionHandler(session: Session, handlers: Handlers) {
  const { onEditOpen, onRescheduleOpen, onFollowUpOpen, onSkip } = handlers;
  return (key: Key) => {
    if (key === "edit-structure") onEditOpen();
    if (key === "reschedule") onRescheduleOpen();
    if (key === "follow-up") onFollowUpOpen();
    if (
      key === "skip" &&
      session.seriesId !== null &&
      session.occurrenceDate !== null
    ) {
      onSkip({
        seriesId: session.seriesId,
        occurrenceDate: session.occurrenceDate,
      });
    }
  };
}

export function SessionActions({
  slug,
  session,
  canEditStructure,
  canManage,
  editing = false,
}: Readonly<Props>) {
  const [editOpen, setEditOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [followUpOpen, setFollowUpOpen] = useState(false);
  const sectionTypes = useSectionTypes();
  const reschedule = useRescheduleSession(slug);
  const skip = useSkipOccurrence(slug);

  const {
    showEditStructure,
    showReschedule,
    showSkip,
    showOwnPlace,
    showFollowUp,
    hasActions,
  } = computeActions(session, canEditStructure, canManage, editing);

  if (!hasActions) return null;

  // The dialog seeds its own drafts on mount, so opening it is all this has to do - apart from
  // clearing the refusal a previous attempt left behind, which would otherwise greet the next one
  // already on screen.
  function openReschedule() {
    reschedule.reset();
    setRescheduleOpen(true);
  }

  const handleAction = createActionHandler(session, {
    onEditOpen: () => setEditOpen(true),
    onRescheduleOpen: openReschedule,
    onFollowUpOpen: () => setFollowUpOpen(true),
    onSkip: skip.mutate,
  });

  return (
    <>
      <MenuTrigger>
        <Button variant="ghost" size="sm" aria-label="Session actions">
          ⋯
        </Button>
        <Popover className={menuPopoverClass}>
          <AriaMenu onAction={handleAction} className="outline-none">
            {showEditStructure && (
              <MenuItem id="edit-structure">
                <Pencil className="h-3.5 w-3.5 shrink-0" />
                Edit
              </MenuItem>
            )}
            {showReschedule && (
              <MenuItem id="reschedule">
                <CalendarClock className="h-3.5 w-3.5 shrink-0" />
                Reschedule
              </MenuItem>
            )}
            {showFollowUp && (
              <MenuItem id="follow-up">
                <CirclePlay className="h-3.5 w-3.5 shrink-0" />
                Recording and note
              </MenuItem>
            )}
            {showSkip && (
              <MenuItem id="skip">
                <SkipForward className="h-3.5 w-3.5 shrink-0" />
                Skip
              </MenuItem>
            )}
          </AriaMenu>
        </Popover>
      </MenuTrigger>

      {showFollowUp && (
        <FollowUpDialog
          slug={slug}
          sessionId={session.id}
          recordingUrl={session.recordingUrl}
          notes={session.notes}
          isOpen={followUpOpen}
          onOpenChange={setFollowUpOpen}
        />
      )}

      {showEditStructure && (
        <Dialog
          isOpen={editOpen}
          onOpenChange={setEditOpen}
          title="Edit session"
        >
          {({ close }) => (
            <SessionEditBody
              slug={slug}
              session={session}
              sectionTypes={sectionTypes}
              canEditLength={canManage}
              showOwnPlace={showOwnPlace}
              onDone={close}
            />
          )}
        </Dialog>
      )}

      {showReschedule && (
        <RescheduleDialog
          slug={slug}
          isOpen={rescheduleOpen}
          onOpenChange={setRescheduleOpen}
          target={targetFromSession(session)}
          isPending={reschedule.isPending}
          isError={reschedule.isError}
          onConfirm={(input) =>
            reschedule.mutate(
              { sessionId: session.id, input },
              // Closes only once the write is accepted. The join link is validated server-side,
              // so a scheme-less paste is a 400, and closing regardless threw away the new date
              // along with it and said nothing.
              { onSuccess: () => setRescheduleOpen(false) },
            )
          }
        />
      )}
    </>
  );
}
