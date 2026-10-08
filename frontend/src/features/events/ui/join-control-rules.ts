import type { JoinPhase } from "@/features/events/domain/join-window";

interface Stance {
  open: boolean;
  label: string | null;
  statesShut: boolean;
}

/** Whether the stacked form has anything worth a row. A phone card is given the news, but not a
 *  control that does nothing: a shut Join there would spend one of its few rows repeating the chip
 *  above it. A surface that asked to state the shut way in always has that to say, and so is never
 *  silent. */
function saysNothing(stance: Readonly<Stance>): boolean {
  return !stance.statesShut && !stance.open && stance.label === null;
}

/**
 * The one thing that can take an untimed occurrence's way in away. A timed one has a window that
 * shuts on its own once the session has run; an untimed one has no window at all, so left to the
 * clock its way in would sit open for good on a session cancelled or closed out months ago - which
 * is what the clock used to prevent. Nothing is rendered rather than a shut Join, because the shut
 * control's only sentence is that the way in opens five minutes before the session, and for this
 * one it never will.
 *
 * A timed occurrence is deliberately not gated here. Its window is what governs it, exactly as
 * before, and nothing about this change touches that.
 */
function offersNoWayIn(hasTime: boolean, stillToHappen: boolean): boolean {
  return !hasTime && !stillToHappen;
}

/**
 * Whether the shut way in is worth stating, for a surface that asked. Only ahead of a session that
 * is still going to happen: the shut control's one sentence is that the way in opens five minutes
 * before, and for a session already run, cancelled or skipped it never will. Those render nothing,
 * exactly as before, rather than a control promising a wait that has no end.
 */
export function worthStating(
  asked: boolean,
  phase: JoinPhase | null,
  stillToHappen: boolean,
): boolean {
  return asked && stillToHappen && phase !== "over";
}

export interface Verdict {
  link: string;
  hasTime: boolean;
  stillToHappen: boolean;
  fullWidth: boolean;
  stance: Stance;
}

/** Whether this instance has nothing at all to offer: no link recorded, an untimed occurrence
 *  whose way in has already been taken away, or a phone layout with nothing worth its row. */
export function rendersNothing({
  link,
  hasTime,
  stillToHappen,
  fullWidth,
  stance,
}: Readonly<Verdict>): boolean {
  return (
    link === "" ||
    offersNoWayIn(hasTime, stillToHappen) ||
    (fullWidth && saysNothing(stance))
  );
}
