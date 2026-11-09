import {
  speakerAssignmentSchema,
  topicSchema,
} from "@/features/events/domain/topic";
import { topicCountsSchema } from "@/features/events/domain/topic-counts";
import type {
  TopicEditInput,
  TopicGateway,
  TopicInput,
  TopicListParams,
} from "@/features/events/domain/topic-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { pageSchema } from "@/lib/page";
import { buildQuery as query } from "@/lib/query";

const topicPageSchema = pageSchema(topicSchema);

export const topicEndpoints: TopicGateway = {
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

  async editTopic(topicId, input: TopicEditInput) {
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

  async addSelfAsSpeaker(topicId) {
    const data = await apiFetch<unknown>(
      `/axonia/topics/${topicId}/speakers/me`,
      { method: "POST" },
    );
    return topicSchema.parse(data);
  },

  async addSpeaker(topicId, speaker) {
    const data = await apiFetch<unknown>(`/axonia/topics/${topicId}/speakers`, {
      method: "POST",
      body: JSON.stringify({ speaker }),
    });
    return speakerAssignmentSchema.parse(data);
  },

  async withdrawAsSpeaker(topicId) {
    const data = await apiFetch<unknown>(
      `/axonia/topics/${topicId}/speakers/me`,
      { method: "DELETE" },
    );
    return topicSchema.parse(data);
  },

  async removeSpeaker(topicId, userId) {
    const data = await apiFetch<unknown>(
      `/axonia/topics/${topicId}/speakers/${userId}`,
      { method: "DELETE" },
    );
    return topicSchema.parse(data);
  },
};
