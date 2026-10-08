import { useEffect, useRef, useState } from "react";

import type { OnboardingView } from "@/features/onboarding/domain/onboarding";

/** How long the finish chip stands before the top bar goes back to what it was. */
const LINGER_MS = 4200;

/**
 * Whether the path finished just now, in front of this person.
 *
 * The server closes a path in the same write that ticks its last step, and answers `COMPLETED`
 * rather than `CLOSED` for one that ran out of steps rather than being dismissed. So the moment
 * worth marking is exactly the transition `OPEN` to `COMPLETED` observed while mounted: a reload
 * afterwards reads `COMPLETED` with nothing before it and marks nothing, which is right - a
 * celebration is a moment, not a state - and somebody who hides the panel one step from the end
 * is never congratulated for it.
 */
export function useFinishMoment(
  state: OnboardingView["state"] | undefined,
): boolean {
  const previous = useRef(state);
  const [finished, setFinished] = useState(false);

  useEffect(() => {
    const wasOpen = previous.current === "OPEN";
    previous.current = state;
    if (wasOpen && state === "COMPLETED") setFinished(true);
  }, [state]);

  // The countdown is its own effect, keyed on the flag rather than folded into the one above:
  // under StrictMode an effect is invoked twice on mount, and a timer started in the first pass
  // and cleared by its cleanup would never be started again by the second - leaving the chip in
  // the top bar for good.
  useEffect(() => {
    if (!finished) return undefined;
    const timer = setTimeout(() => setFinished(false), LINGER_MS);
    return () => clearTimeout(timer);
  }, [finished]);

  return finished;
}
