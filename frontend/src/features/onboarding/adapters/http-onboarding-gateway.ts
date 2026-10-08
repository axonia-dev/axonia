import { onboardingViewSchema } from "@/features/onboarding/domain/onboarding";
import type { OnboardingGateway } from "@/features/onboarding/domain/onboarding-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";

const BASE = "/axonia/me/onboarding";

export const httpOnboardingGateway: OnboardingGateway = {
  async view() {
    return onboardingViewSchema.parse(await apiFetch<unknown>(BASE));
  },

  async assign(communitySlug) {
    const data = await apiFetch<unknown>(BASE, {
      method: "POST",
      body: JSON.stringify({ communitySlug }),
    });
    return onboardingViewSchema.parse(data);
  },

  async visit(stepKey) {
    await apiFetchVoid(`${BASE}/visits`, {
      method: "POST",
      body: JSON.stringify({ stepKey }),
    });
  },

  async close() {
    await apiFetchVoid(`${BASE}/close`, { method: "POST" });
  },
};
