import { z } from "zod";

import type { CommunityGateway } from "@/features/community/domain/community-gateway";
import { communityKindSchema } from "@/features/community/domain/community-kind";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";

const communityKindListSchema = z.array(communityKindSchema);

export const httpCommunityKindCalls: Pick<
  CommunityGateway,
  "listKinds" | "createKind" | "renameKind" | "deleteKind"
> = {
  async listKinds() {
    const data = await apiFetch<unknown>("/axonia/community-kinds");
    return communityKindListSchema.parse(data);
  },

  async createKind(label: string) {
    const data = await apiFetch<unknown>("/axonia/community-kinds", {
      method: "POST",
      body: JSON.stringify({ label }),
    });
    return communityKindSchema.parse(data);
  },

  async renameKind(id: number, label: string) {
    const data = await apiFetch<unknown>(`/axonia/community-kinds/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ label }),
    });
    return communityKindSchema.parse(data);
  },

  async deleteKind(id: number, reassignToId?: number) {
    const suffix =
      reassignToId === undefined ? "" : `?reassignTo=${reassignToId}`;
    await apiFetchVoid(`/axonia/community-kinds/${id}${suffix}`, {
      method: "DELETE",
    });
  },
};
