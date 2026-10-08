import { accessMeSchema } from "@/features/admin/domain/access";
import type { AccessGateway } from "@/features/admin/domain/access-gateway";
import { apiFetch } from "@/lib/api-client";

export const httpAccessGateway: AccessGateway = {
  async getMe() {
    const data = await apiFetch<unknown>("/axonia/access/me");
    return accessMeSchema.parse(data);
  },
};
