import { KeyRound, MapPin } from "lucide-react";
import type { ReactNode } from "react";

import { CopyButton } from "@/components/ui/CopyButton";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { JoinControl } from "@/features/events/ui/JoinControl";

interface Props {
  /** The resolved place, never the override: this is what the session actually meets in. */
  place: MeetingPlace;
  scheduledAt: string;
  /** Whether anybody chose an hour for this occurrence. Handed on to the way in, which otherwise
   *  reads a phase off a placeholder midnight. */
  hasTime: boolean;
  durationMinutes: number | null;
  /** Decides whether the joining details, and an untimed occurrence's way in, are still worth
   *  showing: see {@link stillToHappen}. */
  status: Session["status"];
  /** Handed to the way in and to nothing else. A card names the location and the joining details
   *  once, but offers the way in where each layout has room for it - compact beside a wide
   *  header, full width at the foot of a phone - so only the control is told twice. */
  joinClassName?: string;
  joinFullWidth?: boolean;
  /** Passed straight to the way in: see {@link JoinControl}'s prop of the same name. */
  phaseStatedElsewhere?: boolean;
}

interface LineProps {
  icon: ReactNode;
  children: ReactNode;
}

/** One fact about the place, glyph pinned to its first line so a multi-line value does not push
 *  the icon to the middle of the block. */
function PlaceLine({ icon, children }: Readonly<LineProps>) {
  return (
    <div className="flex items-start gap-2 text-sm">
      <span className="text-muted-foreground mt-0.5 shrink-0">{icon}</span>
      {children}
    </div>
  );
}

/**
 * The joining details, which are the one part of a place nobody reads: they are a meeting ID and a
 * passcode to be pasted somewhere else. Set in a monospace face, because a passcode is a string of
 * characters that have to be told apart rather than a phrase, and offered with the control that
 * puts them on the clipboard so getting in does not depend on retyping them correctly.
 *
 * Line breaks are kept. A dial-in block is written as several lines - a number, an ID, a passcode -
 * and collapsing them into one run is how a member ends up reading a conference ID as part of a
 * phone number.
 */
function JoiningDetails({ value }: Readonly<{ value: string }>) {
  return (
    <PlaceLine icon={<KeyRound aria-hidden className="h-4 w-4" />}>
      <span className="min-w-0 flex-1 font-mono text-xs leading-5 break-words whitespace-pre-line">
        {value}
      </span>
      <CopyButton value={value} label="Copy the joining details" iconOnly />
    </PlaceLine>
  );
}

/**
 * Whether this session is still going to happen, which is the one question both the joining details
 * and an untimed occurrence's way in turn on. `SCHEDULED` includes a session that is running, so
 * nothing is taken off the screen of anybody still trying to get in.
 *
 * The joining details exist for one purpose - getting a member through the door of a session that
 * has not happened yet - so their gate is on status and never on the join window: they have to be
 * readable hours ahead, long before the way in opens. Once a session is cancelled, skipped or closed
 * out, that purpose is spent, and what is left is a spent credential taking up a line of the card
 * with nothing it can be used for. This is a relevance decision and nothing more: it withholds no
 * value from anybody who could not already have it, because the server sends the same passcode on
 * this session either way - it also rides `placeOverride` and `seriesPlace`, which the edit form
 * legitimately needs.
 *
 * The way in asks the same question for a different reason. An untimed occurrence has no window to
 * shut, so its way in is offered outright; unasked, that offer would sit on a cancelled or
 * closed-out session for good, where the clock used to take it away. One predicate answers both,
 * because both are the same fact about the session.
 *
 * The location is gated by neither. It is a fact about the session rather than a way in or a
 * credential, and "this met in Room 4B" stays worth reading on the record of a session that
 * happened.
 */
export function stillToHappen(status: Session["status"]): boolean {
  return status === "SCHEDULED";
}

/**
 * Where the session meets, as one block rather than as loose lines.
 *
 * The parts used to be muted lines in the card's own flow, in the same weight as the minute budget
 * directly above them, so three unrelated facts stacked up as one run of grey text and a passcode
 * read as debris. Grouped on a sunken ground they are visibly one answer to one question, and
 * separate from the meter that happens to sit next to them.
 *
 * Each part appears only when there is one, and the whole block is withheld when there is neither,
 * so a place that is only a room number renders one line rather than an empty panel.
 *
 * The way in stays outside the block. It is a control rather than a fact, and the layouts place it
 * differently: compact beside a wide header, full width at the foot of a phone.
 */
export function SessionPlace({
  place,
  scheduledAt,
  hasTime,
  durationMinutes,
  status,
  joinClassName,
  joinFullWidth = false,
  phaseStatedElsewhere = false,
}: Readonly<Props>) {
  const showDetails = place.joiningDetails !== null && stillToHappen(status);
  return (
    <>
      {(place.location !== null || showDetails) && (
        <div className="bg-surface-sunken flex flex-col gap-2 rounded-md px-3.5 py-3">
          {place.location !== null && (
            <PlaceLine icon={<MapPin aria-hidden className="h-4 w-4" />}>
              <span className="min-w-0 flex-1">{place.location}</span>
            </PlaceLine>
          )}
          {showDetails && <JoiningDetails value={place.joiningDetails ?? ""} />}
        </div>
      )}
      <JoinControl
        joinUrl={place.joinUrl}
        scheduledAt={scheduledAt}
        hasTime={hasTime}
        stillToHappen={stillToHappen(status)}
        durationMinutes={durationMinutes}
        {...(joinClassName !== undefined ? { className: joinClassName } : {})}
        fullWidth={joinFullWidth}
        phaseStatedElsewhere={phaseStatedElsewhere}
      />
    </>
  );
}
