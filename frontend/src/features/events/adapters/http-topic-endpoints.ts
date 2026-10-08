import type {
  EventsGateway,
  TopicInput,
  TopicListParams,
} from "@/features/events/domain/events-gateway";
import { topicSchema } from "@/features/events/domain/topic";
import { topicCountsSchema } from "@/features/events/domain/topic-counts";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { pageSchema } from "@/lib/page";
import { buildQuery as query } from "@/lib/query";

const topicPageSchema = pageSchema(topicSchema);

export const topicEndpoints: Pick<
  EventsGateway,
  | "listTopics"
  | "getTopicCounts"
  | "proposeTopic"
  | "voteTopic"
  | "unvoteTopic"
  | "editTopic"
  | "deleteTopic"
  | "returnToBacklog"
> = {
  async listTopics(slug, params: TopicListParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/topics${query({ ...params })}`,
    );
    return topicPageSchema.parse(data);
  },

  async getTopicCounts(slug) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/topics/counts`,
    );
    return topicCountsSchema.parse(data);
  },

  async proposeTopic(slug, input: TopicInput) {
    const data = await apiFetch<unknown>(`/axonia/communities/${slug}/topics`, {
      method: "POST",
      body: JSON.stringify(input),
    });
    return topicSchema.parse(data);
  },

  async voteTopic(topicId) {
    const data = await apiFetch<unknown>(`/axonia/topics/${topicId}/votes`, {
      method: "POST",
    });
    return topicSchema.parse(data);
  },

  async unvoteTopic(topicId) {
    await apiFetchVoid(`/axonia/topics/${topicId}/votes/me`, {
      method: "DELETE",
    });
  },

  async editTopic(topicId, input: TopicInput) {
    const data = await apiFetch<unknown>(`/axonia/topics/${topicId}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return topicSchema.parse(data);
  },

  async deleteTopic(topicId) {
    await apiFetchVoid(`/axonia/topics/${topicId}`, { method: "DELETE" });
  },

  async returnToBacklog(topicId) {
    await apiFetchVoid(`/axonia/topics/${topicId}/schedule`, {
      method: "DELETE",
    });
  },
};
