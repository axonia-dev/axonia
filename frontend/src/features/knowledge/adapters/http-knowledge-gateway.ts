import { z } from "zod";

import { artifactSchema } from "@/features/knowledge/domain/artifact";
import { artifactSessionSchema } from "@/features/knowledge/domain/artifact-session";
import { artifactTypeSchema } from "@/features/knowledge/domain/artifact-type";
import type {
  ArtifactSearchParams,
  ArtifactTypeInput,
  CaptureArtifactInput,
  EditArtifactInput,
  KnowledgeGateway,
} from "@/features/knowledge/domain/knowledge-gateway";
import { libraryStatsSchema } from "@/features/knowledge/domain/library-stats";
import type {
  AttachResourceInput,
  DetachResourceInput,
} from "@/features/knowledge/domain/session-resource";
import {
  attachmentSchema,
  sessionResourceSchema,
} from "@/features/knowledge/domain/session-resource";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { pageSchema } from "@/lib/page";
import { buildQuery as query } from "@/lib/query";

const artifactPageSchema = pageSchema(artifactSchema);
const artifactTypeListSchema = z.array(artifactTypeSchema);
const artifactSessionListSchema = z.array(artifactSessionSchema);

export const httpKnowledgeGateway: KnowledgeGateway = {
  async search(slug: string, params: ArtifactSearchParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/artifacts${query({ ...params })}`,
    );
    return artifactPageSchema.parse(data);
  },

  async capture(slug: string, input: CaptureArtifactInput) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/artifacts`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
    return artifactSchema.parse(data);
  },

  async listTypes() {
    const data = await apiFetch<unknown>("/axonia/artifact-types");
    return artifactTypeListSchema.parse(data);
  },

  async createType(input: ArtifactTypeInput) {
    const data = await apiFetch<unknown>("/axonia/artifact-types", {
      method: "POST",
      body: JSON.stringify(input),
    });
    return artifactTypeSchema.parse(data);
  },

  async updateType(id: number, input: ArtifactTypeInput) {
    const data = await apiFetch<unknown>(`/axonia/artifact-types/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return artifactTypeSchema.parse(data);
  },

  async deleteType(id: number, reassignToId?: number) {
    const suffix =
      reassignToId === undefined ? "" : `?reassignTo=${reassignToId}`;
    await apiFetchVoid(`/axonia/artifact-types/${id}${suffix}`, {
      method: "DELETE",
    });
  },

  async listSessions(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/artifacts/sessions`,
    );
    return artifactSessionListSchema.parse(data);
  },

  async stats(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/artifacts/stats`,
    );
    return libraryStatsSchema.parse(data);
  },

  async sessionResources(slug: string, sessionId: number) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/sessions/${sessionId}/resources`,
    );
    return z.array(sessionResourceSchema).parse(data);
  },

  async attachResource(
    slug: string,
    sessionId: number,
    input: AttachResourceInput,
  ) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/sessions/${sessionId}/resources`,
      { method: "POST", body: JSON.stringify(input) },
    );
    return attachmentSchema.parse(data);
  },

  async detachResource(
    slug: string,
    sessionId: number,
    { artifactId, segmentId }: DetachResourceInput,
  ) {
    const segment = segmentId === null ? "" : `?segmentId=${segmentId}`;
    await apiFetchVoid(
      `/axonia/communities/${slug}/sessions/${sessionId}/resources/${artifactId}${segment}`,
      { method: "DELETE" },
    );
  },

  async edit(id: number, input: EditArtifactInput) {
    const data = await apiFetch<unknown>(`/axonia/artifacts/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return artifactSchema.parse(data);
  },

  async changeContributor(id: number, contributor: string) {
    const data = await apiFetch<unknown>(
      `/axonia/artifacts/${id}/contributor`,
      {
        method: "PUT",
        body: JSON.stringify({ contributor }),
      },
    );
    return artifactSchema.parse(data);
  },

  async remove(id: number) {
    await apiFetchVoid(`/axonia/artifacts/${id}`, { method: "DELETE" });
  },

  async recordOpening(artifactId: number) {
    await apiFetchVoid(`/axonia/artifacts/${artifactId}/openings`, {
      method: "POST",
    });
  },
};
