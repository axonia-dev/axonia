import type { EventsGateway } from "@/features/events/domain/events-gateway";
import { sessionFeedbackSchema } from "@/features/events/domain/session-feedback";
import { apiFetch } from "@/lib/api-client";

export const feedbackEndpoints: Pick<
  EventsGateway,
  "getSessionFeedback" | "giveSessionFeedback"
> = {
  async getSessionFeedback(sessionId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/feedback`,
    );
    return sessionFeedbackSchema.parse(data);
  },

  async giveSessionFeedback(sessionId, answer) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/feedback/me`,
      { method: "PUT", body: JSON.stringify(answer) },
    );
    return sessionFeedbackSchema.parse(data);
  },
};
