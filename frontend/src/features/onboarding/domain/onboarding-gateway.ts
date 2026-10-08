import type {
  OnboardingStepKey,
  OnboardingView,
} from "@/features/onboarding/domain/onboarding";

export interface OnboardingGateway {
  view(): Promise<OnboardingView>;
  assign(communitySlug: string): Promise<OnboardingView>;
  visit(stepKey: OnboardingStepKey): Promise<void>;
  close(): Promise<void>;
}
