export const onboardingKeys = {
  all: ["onboarding"] as const,
  view: () => [...onboardingKeys.all, "view"] as const,
};
