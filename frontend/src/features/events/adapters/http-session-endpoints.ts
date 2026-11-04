import type {
  EventsGateway,
  UpdateSessionInput,
} from "@/features/events/domain/events-gateway";
import { sessionSchema } from "@/features/events/domain/session";
import { apiFetch } from "@/lib/api-client";

/** One session, read and changed by its id. */
export const sessionEndpoints: Pick<
  EventsGateway,
  "getSession" | "updateSession" | "renameSession"
> = {
  async getSession(id) {
    const data = await apiFetch<unknown>(`/axonia/sessions/${id}`);
    return sessionSchema.parse(data);
  },

  async updateSession(id, input: UpdateSessionInput) {
    const data = await apiFetch<unknown>(`/axonia/sessions/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return sessionSchema.parse(data);
  },

  async renameSession(id, title) {
    const data = await apiFetch<unknown>(`/axonia/sessions/${id}/title`, {
      method: "PUT",
      body: JSON.stringify({ title }),
    });
    return sessionSchema.parse(data);
  },
};
