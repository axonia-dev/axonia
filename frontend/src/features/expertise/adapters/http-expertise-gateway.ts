import { z } from "zod";

import {
  areaEvidencePageSchema,
  declaredAreaSchema,
  expertiseAreaSchema,
  mapContributionsSchema,
  mergeSuggestionSchema,
  practitionerSchema,
  selfViewSchema,
  tagUsageSchema,
  topicMapSchema,
} from "@/features/expertise/domain/expertise";
import type {
  DismissInput,
  ExpertiseGateway,
  ExpertiseSearchParams,
} from "@/features/expertise/domain/expertise-gateway";
import { namedAreaSchema } from "@/features/expertise/domain/named-area";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

const practitionerListSchema = z.array(practitionerSchema);
const expertiseAreaListSchema = z.array(expertiseAreaSchema);
const mergeSuggestionListSchema = z.array(mergeSuggestionSchema);
const namedAreaListSchema = z.array(namedAreaSchema);

function namedAreasPath(slug: string): string {
  return `/axonia/communities/${slug}/expertise/curation/named-areas`;
}

function tagsQuery(tags: string[]): string {
  if (tags.length === 0) return "";
  const params = tags.map((t) => `tags=${encodeURIComponent(t)}`).join("&");
  return `?${params}`;
}

/** One page of an area's evidence under a self view: the organization's, or one community's. */
async function areaEvidencePage(
  selfPath: string,
  tag: string,
  offset: number,
  limit: number,
) {
  const data = await apiFetch<unknown>(
    `${selfPath}/areas/${encodeURIComponent(tag)}/evidence${query({ offset: String(offset), limit: String(limit) })}`,
  );
  return areaEvidencePageSchema.parse(data);
}

export const httpExpertiseGateway: ExpertiseGateway = {
  async search(slug: string, params: ExpertiseSearchParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/search${query({ term: params.term, demonstratedOnly: String(params.demonstratedOnly) })}`,
    );
    return practitionerListSchema.parse(data);
  },

  async areas(slug: string, search?: string) {
    const qs = search !== undefined ? query({ search }) : "";
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/areas${qs}`,
    );
    return expertiseAreaListSchema.parse(data);
  },

  async map(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/map`,
    );
    return topicMapSchema.parse(data);
  },

  async mapContributions(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/map/contributions`,
    );
    return mapContributionsSchema.parse(data);
  },

  async suggestions(slug: string, tags: string[]) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/suggestions${tagsQuery(tags)}`,
    );
    return practitionerListSchema.parse(data);
  },

  async selfView() {
    const data = await apiFetch<unknown>("/axonia/expertise/me");
    return selfViewSchema.parse(data);
  },

  async selfViewIn(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/me`,
    );
    return selfViewSchema.parse(data);
  },

  async areaEvidence(tag: string, offset: number, limit: number) {
    return areaEvidencePage("/axonia/expertise/me", tag, offset, limit);
  },

  async areaEvidenceIn(
    slug: string,
    tag: string,
    offset: number,
    limit: number,
  ) {
    return areaEvidencePage(
      `/axonia/communities/${slug}/expertise/me`,
      tag,
      offset,
      limit,
    );
  },

  async setOptOut(optedOut: boolean) {
    await apiFetchVoid("/axonia/expertise/me/opt-out", {
      method: "PUT",
      body: JSON.stringify({ optedOut }),
    });
  },

  async hideTag(tag: string) {
    await apiFetchVoid(
      `/axonia/expertise/me/hidden/${encodeURIComponent(tag)}`,
      { method: "PUT" },
    );
  },

  async unhideTag(tag: string) {
    await apiFetchVoid(
      `/axonia/expertise/me/hidden/${encodeURIComponent(tag)}`,
      { method: "DELETE" },
    );
  },

  async dismiss(input: DismissInput) {
    await apiFetchVoid("/axonia/expertise/me/dismiss", {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  async declare(slug: string, tag: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/declarations`,
      { method: "POST", body: JSON.stringify({ tag }) },
    );
    return declaredAreaSchema.parse(data);
  },

  async removeDeclaration(slug: string, tag: string) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/expertise/declarations/${encodeURIComponent(tag)}`,
      { method: "DELETE" },
    );
  },

  async curationSuggestions(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/curation/suggestions`,
    );
    return mergeSuggestionListSchema.parse(data);
  },

  async curationVocabulary(slug: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/curation/vocabulary`,
    );
    return expertiseAreaListSchema.parse(data);
  },

  async merge(slug: string, variant: string, canonical: string) {
    await apiFetchVoid(`/axonia/communities/${slug}/expertise/curation/merge`, {
      method: "POST",
      body: JSON.stringify({ variant, canonical }),
    });
  },

  async mergeBatch(slug: string, variants: string[], canonical: string) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/expertise/curation/merge-batch`,
      { method: "POST", body: JSON.stringify({ variants, canonical }) },
    );
  },

  async split(slug: string, variant: string) {
    await apiFetchVoid(`/axonia/communities/${slug}/expertise/curation/split`, {
      method: "POST",
      body: JSON.stringify({ variant }),
    });
  },

  async dismissSuggestion(slug: string, tagA: string, tagB: string) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/expertise/curation/dismiss`,
      { method: "POST", body: JSON.stringify({ tagA, tagB }) },
    );
  },

  async restoreSuggestion(slug: string, tagA: string, tagB: string) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/expertise/curation/undismiss`,
      { method: "POST", body: JSON.stringify({ tagA, tagB }) },
    );
  },

  async tagUsage(slug: string, tag: string) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/expertise/curation/tags/${encodeURIComponent(tag)}/usage`,
    );
    return tagUsageSchema.parse(data);
  },

  async renameTag(slug: string, from: string, to: string) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/expertise/curation/rename`,
      {
        method: "POST",
        body: JSON.stringify({ from, to }),
      },
    );
  },

  async deleteTag(slug: string, tag: string) {
    await apiFetchVoid(
      `/axonia/communities/${slug}/expertise/curation/delete`,
      {
        method: "POST",
        body: JSON.stringify({ tag }),
      },
    );
  },

  async namedAreas(slug: string) {
    const data = await apiFetch<unknown>(namedAreasPath(slug));
    return namedAreaListSchema.parse(data);
  },

  async createNamedArea(slug: string, name: string) {
    const data = await apiFetch<unknown>(namedAreasPath(slug), {
      method: "POST",
      body: JSON.stringify({ name }),
    });
    return namedAreaSchema.parse(data);
  },

  async renameNamedArea(slug: string, id: number, name: string) {
    await apiFetchVoid(`${namedAreasPath(slug)}/${id}/rename`, {
      method: "POST",
      body: JSON.stringify({ name }),
    });
  },

  async deleteNamedArea(slug: string, id: number) {
    await apiFetchVoid(`${namedAreasPath(slug)}/${id}/delete`, {
      method: "POST",
    });
  },

  async placeInNamedArea(slug: string, id: number, tag: string) {
    await apiFetchVoid(`${namedAreasPath(slug)}/${id}/tags`, {
      method: "POST",
      body: JSON.stringify({ tag }),
    });
  },

  async removeFromNamedArea(slug: string, tag: string) {
    await apiFetchVoid(
      `${namedAreasPath(slug)}/tags/${encodeURIComponent(tag)}/remove`,
      { method: "POST" },
    );
  },
};
