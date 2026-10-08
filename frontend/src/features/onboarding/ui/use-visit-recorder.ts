import { useEffect } from "react";
import { useLocation } from "react-router-dom";

import { useRecordVisit } from "@/features/onboarding/api/use-onboarding";
import type { OnboardingPath } from "@/features/onboarding/domain/onboarding";

/**
 * Ticks a step off by arriving at it. The comparison is against the step's own frozen route
 * rather than a route pattern: two member steps can point at two different sessions, and a
 * pattern match would tick both from one arrival. The server write is idempotent, so a
 * re-render or a back-button re-visit here is harmless by design.
 */
export function useVisitRecorder(path: OnboardingPath | null): void {
  const { pathname, search } = useLocation();
  const { mutate } = useRecordVisit();

  useEffect(() => {
    if (path === null) return;
    const here = `${pathname}${search}`;
    const arrived = path.steps.find(
      (step) => !step.visited && step.route === here,
    );
    if (arrived !== undefined) mutate(arrived.key);
  }, [path, pathname, search, mutate]);
}
