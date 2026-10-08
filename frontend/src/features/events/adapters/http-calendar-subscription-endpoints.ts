import { calendarSubscriptionSchema } from "@/features/events/domain/calendar-subscription";
import type { EventsGateway } from "@/features/events/domain/events-gateway";
import { ApiError, apiFetch } from "@/lib/api-client";

const base = (slug: string) =>
  `/axonia/communities/${slug}/calendar-subscription`;

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

export const calendarSubscriptionEndpoints: Pick<
  EventsGateway,
  | "getCalendarSubscription"
  | "subscribeToCalendar"
  | "resetCalendarSubscription"
> = {
  /** A member who has never subscribed is a 404 on the wire and a null here: absence is a
   *  state the dialog offers to change, not a failure it has to report. */
  async getCalendarSubscription(slug) {
    try {
      const data = await apiFetch<unknown>(base(slug));
      return calendarSubscriptionSchema.parse(data);
    } catch (error) {
      if (isNotFound(error)) return null;
      throw error;
    }
  },

  async subscribeToCalendar(slug) {
    const data = await apiFetch<unknown>(base(slug), { method: "POST" });
    return calendarSubscriptionSchema.parse(data);
  },

  async resetCalendarSubscription(slug) {
    const data = await apiFetch<unknown>(`${base(slug)}/reset`, {
      method: "POST",
    });
    return calendarSubscriptionSchema.parse(data);
  },
};
