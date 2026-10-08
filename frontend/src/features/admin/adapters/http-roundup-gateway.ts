import type { RoundupGateway } from "@/features/admin/domain/roundup-gateway";
import {
  roundupPreviewSchema,
  roundupSettingsSchema,
} from "@/features/admin/domain/roundup-settings";
import { apiFetch } from "@/lib/api-client";

const SETTINGS_PATH = "/axonia/roundup/settings";

export const httpRoundupGateway: RoundupGateway = {
  async getSettings() {
    const data = await apiFetch<unknown>(SETTINGS_PATH);
    return roundupSettingsSchema.parse(data);
  },

  async setEnabled(enabled: boolean) {
    const data = await apiFetch<unknown>(SETTINGS_PATH, {
      method: "PUT",
      body: JSON.stringify({ enabled }),
    });
    return roundupSettingsSchema.parse(data);
  },

  async getPreview() {
    const data = await apiFetch<unknown>("/axonia/roundup/preview");
    return roundupPreviewSchema.parse(data);
  },
};
