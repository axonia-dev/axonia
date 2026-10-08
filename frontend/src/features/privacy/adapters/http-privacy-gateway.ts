import { apiFetch, apiFetchVoid } from "@/lib/api-client";

import type { PrivacyGateway } from "../domain/erasure";
import { erasurePreflightSchema } from "../domain/erasure";

export const httpPrivacyGateway: PrivacyGateway = {
  async erasurePreflight() {
    const data = await apiFetch<unknown>(
      "/axonia/me/account/erasure-preflight",
    );
    return erasurePreflightSchema.parse(data);
  },

  async eraseAccount() {
    await apiFetchVoid("/axonia/me/account", { method: "DELETE" });
  },
};
