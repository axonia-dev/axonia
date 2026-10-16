import type { EventsGateway } from "@/features/events/domain/events-gateway";
import { memoryPageSchema } from "@/features/events/domain/memory";
import { ribbonSchema } from "@/features/events/domain/ribbon";
import { apiFetch } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

export const memoryEndpoints: Pick<EventsGateway, "getMemories" | "getRibbon"> =
  {
    async getMemories(slug, before, size) {
      const data = await apiFetch<unknown>(
        `/axonia/communities/${slug}/schedule/memories${query({ before: before?.at, beforeId: before?.id, size })}`,
      );
      return memoryPageSchema.parse(data);
    },

    async getRibbon(slug, fromIso, toIso) {
      const data = await apiFetch<unknown>(
        `/axonia/communities/${slug}/schedule/ribbon${query({ from: fromIso, to: toIso })}`,
      );
      return ribbonSchema.parse(data);
    },
  };
