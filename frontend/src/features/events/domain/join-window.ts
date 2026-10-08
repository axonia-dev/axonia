export type JoinPhase = "far" | "soon" | "open" | "live" | "over";

/** Exported so a test that needs an instant inside a particular phase can derive it from the
 *  same numbers this module judges by, rather than a hardcoded offset that silently drifts out
 *  of phase if these ever change. */
export const SOON_MINUTES = 60;
export const OPEN_MINUTES = 5;
/**
 * Erring long only keeps the way in reachable a little past a session that already finished, while
 * erring short shuts it on one still running. The calendar feed uses a shorter 60-minute fallback for
 * DTEND for the opposite reason: over-reserving somebody's real calendar is worse than under-reserving it.
 */
const FALLBACK_MINUTES = 90;

interface Input {
  scheduledAt: string;
  durationMinutes: number | null;
  now: Date;
}

/** The four instants a phase can turn at, in order, and the instant being judged. */
function marks({ scheduledAt, durationMinutes, now }: Readonly<Input>) {
  const start = new Date(scheduledAt).getTime();
  return {
    at: now.getTime(),
    soonAt: start - SOON_MINUTES * 60_000,
    openAt: start - OPEN_MINUTES * 60_000,
    start,
    end: start + (durationMinutes ?? FALLBACK_MINUTES) * 60_000,
  };
}

export function joinPhase(input: Readonly<Input>): JoinPhase {
  const { at, soonAt, openAt, start, end } = marks(input);

  if (at >= end) {
    return "over";
  }
  if (at >= start) {
    return "live";
  }
  if (at >= openAt) {
    return "open";
  }
  if (at >= soonAt) {
    return "soon";
  }
  return "far";
}

/**
 * The next instant this session's phase turns, or null once nothing is left to wait for. A control
 * that has to keep telling the truth as the clock moves asks for this rather than polling.
 */
export function nextPhaseChange(input: Readonly<Input>): number | null {
  const { at, soonAt, openAt, start, end } = marks(input);

  return [soonAt, openAt, start, end].find((mark) => mark > at) ?? null;
}

/** The way in is reachable only in the window it is for. */
export function isJoinOpen(phase: JoinPhase): boolean {
  return phase === "open" || phase === "live";
}
