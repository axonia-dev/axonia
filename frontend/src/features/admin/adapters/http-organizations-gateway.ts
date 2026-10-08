import { currentOrganizationSchema } from "@/features/admin/domain/current-organization";
import { myOrganizationSchema } from "@/features/admin/domain/my-organization";
import type { OrganizationsGateway } from "@/features/admin/domain/organizations-gateway";
import { apiFetch } from "@/lib/api-client";

export const httpOrganizationsGateway: OrganizationsGateway = {
  async listMyOrganizations() {
    const data = await apiFetch<unknown>("/axonia/me/organizations");
    return myOrganizationSchema.array().parse(data);
  },

  async getCurrentOrganization() {
    const data = await apiFetch<unknown>("/axonia/organizations/current");
    return currentOrganizationSchema.parse(data);
  },

  async setCommunicationsEnabled(enabled: boolean) {
    const data = await apiFetch<unknown>(
      "/axonia/organizations/current/communications-enabled",
      { method: "PUT", body: JSON.stringify({ enabled }) },
    );
    return currentOrganizationSchema.parse(data);
  },
};
