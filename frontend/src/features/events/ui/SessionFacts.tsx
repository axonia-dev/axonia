import type { ReactNode } from "react";

import { governingZone } from "@/features/events/domain/reschedule-target";
import { runSheet } from "@/features/events/domain/run-sheet";
import type { Session } from "@/features/events/domain/session";
import {
  distinctPresenters,
  slotCount,
} from "@/features/events/domain/session";
import type { SessionPhase } from "@/features/events/domain/session-phase";
import { isOpen } from "@/features/events/domain/slot";
import { instantToZoned } from "@/features/events/domain/zoned-instant";
import { plural } from "@/lib/plural";

function openSpots(session: Session): number {
  return session.sections.reduce(
    (sum, section) => sum + section.slots.filter(isOpen).length,
    0,
  );
}

/**
 * The window the session occupies, read on the clock that governs it.
 *
 * A held session is measured by `totalMinutes`, the run sheet's own record of what the agenda
 * actually ran, a round table included, so the strip can never state a different length than the
 * sheet below it. Every other phase is measured by what was booked instead, falling back to the
 * agenda's content only when nothing was booked at all.
 *
 * The two differ on purpose and must keep differing: a booking is a promise about the future, and
 * an agenda is a record of the past. A session still ahead of us has not yet had the chance to run
 * short or long, so its strip states the hour it was promised to end, not the hour its
 * still-provisional agenda happens to add up to today.
 */
function hoursLabel(
  session: Session,
  phase: SessionPhase,
  totalMinutes: number,
): string {
  if (!session.hasTime) return "Time to be confirmed";
  const zone = governingZone(session);
  const start = new Date(session.scheduledAt);
  const minutes =
    phase === "held" ? totalMinutes : (session.durationMinutes ?? totalMinutes);
  const end = new Date(start.getTime() + minutes * 60_000);
  const from = instantToZoned(start.toISOString(), zone).time;
  if (minutes === 0) return from;
  return `${from} to ${instantToZoned(end.toISOString(), zone).time}`;
}

interface Props {
  session: Session;
  phase: SessionPhase;
  /** This session's outgoing mail, stated by whoever knows who may be told about it. */
  release?: ReactNode;
  /** The recording a held session left behind, or the invitation to hand one in. */
  recording?: ReactNode;
}

// `flex` rather than the default inline flow: one of these facts is a chip, and an inline-flex
// child inside an inline wrapper inflates that wrapper to the row's line box, nudging every text
// fact beside it off centre by a pixel or two.
//
// Each separator leads its fact, so the first one on every line lands in the strip's clipped
// negative margin and no line ends on a stray rule.
function Fact({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <span className="flex items-center gap-2.5">
      <span aria-hidden className="bg-border-strong h-3.5 w-px shrink-0" />
      <span className="flex items-center">{children}</span>
    </span>
  );
}

/**
 * The hard facts, each stated once.
 *
 * It exists so the page's identity is what a reader meets first. A delivery receipt used to be
 * the loudest thing above the title, which put an organizer's ops concern ahead of the session
 * itself for every member who opened the page.
 */
export function SessionFacts({
  session,
  phase,
  release,
  recording,
}: Readonly<Props>) {
  const spots = openSpots(session);
  const showSpots = spots > 0 && (phase === "upcoming" || phase === "awaiting");
  const sheet = runSheet(session);
  const runsOver = sheet.overrunMinutes;
  return (
    <div className="bg-surface-sunken text-muted-foreground rounded px-3 py-2.5 text-xs">
      <div className="-m-1 overflow-hidden p-1">
        <div className="-ml-[calc(--spacing(2.5)+1px)] flex flex-wrap items-center gap-2.5">
          <Fact>{hoursLabel(session, phase, sheet.totalMinutes)}</Fact>
          {phase === "held" && runsOver > 0 && (
            <Fact>
              <span className="text-warning">{runsOver} min over</span>
            </Fact>
          )}
          <Fact>{plural(slotCount(session), "talk")}</Fact>
          <Fact>{plural(distinctPresenters(session).length, "presenter")}</Fact>
          {showSpots && (
            <Fact>
              <span className="text-primary-strong font-semibold">
                {plural(spots, "open spot")}
              </span>
            </Fact>
          )}
          {session.place.location !== null && (
            <Fact>{session.place.location}</Fact>
          )}
          {release && <Fact>{release}</Fact>}
          {recording && <Fact>{recording}</Fact>}
        </div>
      </div>
    </div>
  );
}
