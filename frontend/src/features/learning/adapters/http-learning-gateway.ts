import { z } from "zod";

import {
  heldAreaSchema,
  learnerSchema,
  learningPageSchema,
} from "@/features/learning/domain/learning";
import type { LearningGateway } from "@/features/learning/domain/learning-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";

const learnerListSchema = z.array(learnerSchema);

function base(slug: string): string {
  return `/axonia/communities/${slug}/learning`;
}

export const httpLearningGateway: LearningGateway = {
  async page(slug) {
    return learningPageSchema.parse(await apiFetch<unknown>(base(slug)));
  },

  async learners(slug, tag) {
    const data = await apiFetch<unknown>(
      `${base(slug)}/areas/${encodeURIComponent(tag)}/learners`,
    );
    return learnerListSchema.parse(data);
  },

  async add(slug, tag) {
    const data = await apiFetch<unknown>(`${base(slug)}/intents`, {
      method: "POST",
      body: JSON.stringify({ tag }),
    });
    return heldAreaSchema.parse(data);
  },

  async remove(slug, tag) {
    await apiFetchVoid(`${base(slug)}/intents/${encodeURIComponent(tag)}`, {
      method: "DELETE",
    });
  },
};
