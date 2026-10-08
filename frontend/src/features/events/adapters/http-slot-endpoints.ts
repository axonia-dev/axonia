import {
  presenterAssignmentSchema,
  recordedSlotSchema,
} from "@/features/events/domain/presenter";
import { sessionSchema } from "@/features/events/domain/session";
import type {
  EditSlotInput,
  QueueTopicInput,
  RecordSlotInput,
  SlotGateway,
  SlotInput,
} from "@/features/events/domain/slot-gateway";
import { apiFetch } from "@/lib/api-client";

export const slotEndpoints: Pick<
  SlotGateway,
  | "addSlot"
  | "queueTopic"
  | "reorderSlots"
  | "presentSlot"
  | "withdrawSlot"
  | "editSlot"
  | "removeSlot"
  | "recordSlot"
  | "addPresenter"
  | "removePresenter"
> = {
  async addSlot(sessionId, sectionId, input: SlotInput) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
    return sessionSchema.parse(data);
  },

  async queueTopic(sessionId, sectionId, input: QueueTopicInput) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/from-backlog`,
      { method: "POST", body: JSON.stringify(input) },
    );
    return sessionSchema.parse(data);
  },

  async reorderSlots(sessionId, sectionId, slotIds: number[]) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/order`,
      { method: "PUT", body: JSON.stringify({ slotIds }) },
    );
    return sessionSchema.parse(data);
  },

  async presentSlot(sessionId, sectionId, slotId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/${slotId}/presenters/me`,
      { method: "POST" },
    );
    return sessionSchema.parse(data);
  },

  async withdrawSlot(sessionId, sectionId, slotId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/${slotId}/presenters/me`,
      { method: "DELETE" },
    );
    return sessionSchema.parse(data);
  },

  async editSlot(sessionId, sectionId, slotId, input: EditSlotInput) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/${slotId}`,
      { method: "PUT", body: JSON.stringify(input) },
    );
    return sessionSchema.parse(data);
  },

  async removeSlot(sessionId, sectionId, slotId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/${slotId}`,
      { method: "DELETE" },
    );
    return sessionSchema.parse(data);
  },

  async recordSlot(sessionId, sectionId, input: RecordSlotInput) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/recorded`,
      { method: "POST", body: JSON.stringify(input) },
    );
    return recordedSlotSchema.parse(data);
  },

  async addPresenter(sessionId, sectionId, slotId, presenter) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/${slotId}/presenters`,
      { method: "POST", body: JSON.stringify({ presenter }) },
    );
    return presenterAssignmentSchema.parse(data);
  },

  async removePresenter(sessionId, sectionId, slotId, userId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}/slots/${slotId}/presenters/${userId}`,
      { method: "DELETE" },
    );
    return sessionSchema.parse(data);
  },
};
