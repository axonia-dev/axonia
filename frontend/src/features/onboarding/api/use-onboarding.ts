import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpOnboardingGateway } from "@/features/onboarding/adapters/http-onboarding-gateway";
import { onboardingKeys } from "@/features/onboarding/api/query-keys";
import type { OnboardingStepKey } from "@/features/onboarding/domain/onboarding";

const gateway = httpOnboardingGateway;

function useInvalidate() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: onboardingKeys.view() });
  };
}

// A failed read means no chip, not a broken page.
export function useOnboarding() {
  return useQuery({
    queryKey: onboardingKeys.view(),
    queryFn: () => gateway.view(),
    retry: false,
  });
}

export function useAssignOnboarding() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (communitySlug: string) => gateway.assign(communitySlug),
    onSuccess: invalidate,
  });
}

export function useRecordVisit() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (stepKey: OnboardingStepKey) => gateway.visit(stepKey),
    onSuccess: invalidate,
  });
}

export function useCloseOnboarding() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: () => gateway.close(),
    onSuccess: invalidate,
  });
}
