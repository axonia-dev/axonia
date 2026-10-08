import { useOnboarding } from "@/features/onboarding/api/use-onboarding";
import type { OnboardingPath } from "@/features/onboarding/domain/onboarding";

import { OnboardingChip } from "./OnboardingChip";
import { OnboardingFinishChip } from "./OnboardingFinishChip";
import { useFinishMoment } from "./use-finish-moment";

function openPath(
  view: ReturnType<typeof useOnboarding>["data"],
): OnboardingPath | null {
  if (view === undefined || view.state !== "OPEN") return null;
  return view.path;
}

/** The path a shell should record visits against, or null while there is nothing open. A shell
 *  calls this once, alongside its own single {@link useVisitRecorder} call - the affordance
 *  itself renders in two header slots (mobile and desktop, CSS toggles which is visible), so it
 *  must never be the thing that performs the write. */
export function useOnboardingPath(): OnboardingPath | null {
  const { data } = useOnboarding();
  return openPath(data);
}

/**
 * The single point a shell mounts to show onboarding progress. Renders nothing while the read is
 * loading, on error, and for both ended states: onboarding must never break a shell, so
 * there is no error boundary, no toast, and no retry loop here.
 *
 * The one exception to "ended renders nothing" is the few seconds after the last step ticks, when
 * the finish chip stands in the place the progress chip just left (see {@link useFinishMoment}).
 *
 * Pure presentation: recording a visit is the mounting shell's job (see {@link useOnboardingPath}),
 * not this component's, because this component mounts twice per shell.
 */
export function OnboardingAffordance() {
  const { data } = useOnboarding();
  const finishedJustNow = useFinishMoment(data?.state);
  const path = openPath(data);

  if (path !== null) return <OnboardingChip path={path} />;
  if (finishedJustNow) return <OnboardingFinishChip />;
  return null;
}
