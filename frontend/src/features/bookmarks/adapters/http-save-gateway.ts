import { z } from "zod";

import type { SaveGateway } from "@/features/bookmarks/domain/save-gateway";
import {
  type SaveableRef,
  savedItemSchema,
  type SaveState,
  saveStateSchema,
} from "@/features/bookmarks/domain/saved";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { createBatchLoader } from "@/lib/batch-loader";

const mineSchema = z.array(savedItemSchema);
const artifactStatesSchema = z.array(
  saveStateSchema.extend({ id: z.number() }),
);

/** The server's cap on one batch of artifact save states. */
const MAX_ARTIFACT_STATES = 100;

/** Every card on a page reads its own save state; those reads leave as one request per tick. */
const artifactState = createBatchLoader<SaveState>(async (ids) => {
  const data = await apiFetch<unknown>(
    `/axonia/artifacts/save-states?ids=${ids.join(",")}`,
  );
  return new Map(
    artifactStatesSchema
      .parse(data)
      .map(({ id, viewerHasSaved, viewerHelped }) => [
        id,
        { viewerHasSaved, viewerHelped },
      ]),
  );
}, MAX_ARTIFACT_STATES);

function saveUrl(ref: SaveableRef): string {
  const resource = ref.kind === "artifact" ? "artifacts" : "sessions";
  return `/axonia/${resource}/${ref.id}/save`;
}

export const httpSaveGateway: SaveGateway = {
  async getState(ref) {
    if (ref.kind === "artifact") {
      return artifactState(ref.id);
    }
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
