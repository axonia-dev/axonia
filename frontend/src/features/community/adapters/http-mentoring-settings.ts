import type { CommunityGateway } from "@/features/community/domain/community-gateway";
import {
  type MentoringSettings,
  mentoringSettingsSchema,
} from "@/features/community/domain/mentoring-settings";
import { apiFetch } from "@/lib/api-client";

function path(slug: string): string {
  return `/axonia/communities/${slug}/mentoring-settings`;
}

export const httpMentoringSettingsCalls: Pick<
  CommunityGateway,
  "getMentoringSettings" | "updateMentoringSettings"
> = {
  async getMentoringSettings(slug: string) {
    return mentoringSettingsSchema.parse(await apiFetch<unknown>(path(slug)));
  },

  async updateMentoringSettings(slug: string, settings: MentoringSettings) {
    const data = await apiFetch<unknown>(path(slug), {
      method: "PUT",
      body: JSON.stringify(settings),
    });
    return mentoringSettingsSchema.parse(data);
  },
};
