import { z } from "zod";

import type { IdentityGateway } from "@/features/identity/domain/identity-gateway";
import { userSchema } from "@/features/identity/domain/user";
import { apiFetch } from "@/lib/api-client";

const TIME_ZONE = "/axonia/me/time-zone";

const userListSchema = z.array(userSchema);

export const httpIdentityGateway: IdentityGateway = {
  async me() {
    const data = await apiFetch<unknown>("/axonia/me");
    return userSchema.parse(data);
  },

  async resolve(ids: string[]) {
    const data = await apiFetch<unknown>(
      `/axonia/users?ids=${ids.map(encodeURIComponent).join(",")}`,
    );
    return userListSchema.parse(data);
  },

  async setAvatar(file: File) {
    const body = new FormData();
    body.append("file", file);
    // No Content-Type: fetch derives it with the multipart boundary, and setting it here
    // would append a second value and 415.
    const data = await apiFetch<unknown>("/axonia/me/avatar", {
      method: "PUT",
      body,
    });
    return userSchema.parse(data);
  },

  async clearAvatar() {
    const data = await apiFetch<unknown>("/axonia/me/avatar", {
      method: "DELETE",
    });
    return userSchema.parse(data);
  },

  async declareTimeZone(zone: string) {
    const data = await apiFetch<unknown>(TIME_ZONE, {
      method: "PUT",
      body: JSON.stringify({ zone }),
    });
    return userSchema.parse(data);
  },

  async clearTimeZone() {
    const data = await apiFetch<unknown>(TIME_ZONE, { method: "DELETE" });
    return userSchema.parse(data);
  },

  async reportObservedTimeZone(zone: string) {
    const data = await apiFetch<unknown>(`${TIME_ZONE}/observed`, {
      method: "PUT",
      body: JSON.stringify({ zone }),
    });
    return userSchema.parse(data);
  },
};
