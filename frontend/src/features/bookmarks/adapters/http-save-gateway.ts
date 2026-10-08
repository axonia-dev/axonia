import { z } from "zod";

import type { SaveGateway } from "@/features/bookmarks/domain/save-gateway";
import {
  type SaveableRef,
  savedItemSchema,
  saveStateSchema,
} from "@/features/bookmarks/domain/saved";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";

const mineSchema = z.array(savedItemSchema);

function saveUrl(ref: SaveableRef): string {
  const resource = ref.kind === "artifact" ? "artifacts" : "sessions";
  return `/axonia/${resource}/${ref.id}/save`;
}

export const httpSaveGateway: SaveGateway = {
  async getState(ref) {
    const data = await apiFetch<unknown>(saveUrl(ref));
    return saveStateSchema.parse(data);
  },

  async save(ref) {
    const data = await apiFetch<unknown>(saveUrl(ref), { method: "POST" });
    return saveStateSchema.parse(data);
  },

  async unsave(ref) {
    await apiFetchVoid(saveUrl(ref), { method: "DELETE" });
  },

  async markHelpful(artifactId, helped) {
    const data = await apiFetch<unknown>(
      `/axonia/artifacts/${artifactId}/helpful`,
      { method: "PUT", body: JSON.stringify({ helped }) },
    );
    return saveStateSchema.parse(data);
  },

  async listMine() {
    const data = await apiFetch<unknown>(`/axonia/me/saved`);
    return mineSchema.parse(data);
  },
};
