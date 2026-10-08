import { z } from "zod";

import {
  loveSchema,
  loveSummarySchema,
  recognizedContributionSchema,
} from "@/features/recognition/domain/recognition";
import type { RecognitionGateway } from "@/features/recognition/domain/recognition-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

const myContributionsSchema = z.array(recognizedContributionSchema);

export const httpRecognitionGateway: RecognitionGateway = {
  async getSummary(key) {
    const data = await apiFetch<unknown>(
      `/axonia/recognition/loves${query({ ...key })}`,
    );
    return loveSummarySchema.parse(data);
  },

  async love(input) {
    const data = await apiFetch<unknown>(`/axonia/recognition/loves`, {
      method: "POST",
      body: JSON.stringify(input),
    });
    return loveSchema.parse(data);
  },

  async unlove(key) {
    await apiFetchVoid(`/axonia/recognition/loves${query({ ...key })}`, {
      method: "DELETE",
    });
  },

  async listMyContributions() {
    const data = await apiFetch<unknown>(
      `/axonia/recognition/contributions/me`,
    );
    return myContributionsSchema.parse(data);
  },
};
