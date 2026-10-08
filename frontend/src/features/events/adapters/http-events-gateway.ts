import { z } from "zod";

import { attendanceSchema } from "@/features/events/domain/attendance";
import type {
  CreateOneOffInput,
  EventsGateway,
  PageParams,
  PastSessionParams,
  SectionTemplateInput,
  SeriesInput,
  UpdateSessionInput,
} from "@/features/events/domain/events-gateway";
import { memberZoneListSchema } from "@/features/events/domain/member-zone";
import {
  scheduleActivitySchema,
  scheduleSchema,
} from "@/features/events/domain/schedule";
import { sectionTypeSchema } from "@/features/events/domain/section-type";
import { seriesSchema } from "@/features/events/domain/series";
import { sessionSchema } from "@/features/events/domain/session";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { pageSchema } from "@/lib/page";
import { buildQuery as query } from "@/lib/query";

import { actionItemEndpoints } from "./http-action-item-endpoints";
import { calendarSubscriptionEndpoints } from "./http-calendar-subscription-endpoints";
import { slotEndpoints } from "./http-slot-endpoints";
import { topicEndpoints } from "./http-topic-endpoints";

const seriesPageSchema = pageSchema(seriesSchema);
const sessionPageSchema = pageSchema(sessionSchema);
const attendancePageSchema = pageSchema(attendanceSchema);
const sectionTypeListSchema = z.array(sectionTypeSchema);
const messagePreviewSchema = z.object({
  subject: z.string(),
  html: z.string(),
});

export const httpEventsGateway: EventsGateway = {
  ...slotEndpoints,
  ...topicEndpoints,
  ...calendarSubscriptionEndpoints,
  ...actionItemEndpoints,

  async listSectionTypes() {
    const data = await apiFetch<unknown>("/axonia/section-types");
    return sectionTypeListSchema.parse(data);
  },

  async createSectionType(input) {
    const data = await apiFetch<unknown>("/axonia/section-types", {
      method: "POST",
      body: JSON.stringify(input),
    });
    return sectionTypeSchema.parse(data);
  },

  async updateSectionType(id, input) {
    const data = await apiFetch<unknown>(`/axonia/section-types/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return sectionTypeSchema.parse(data);
  },

  async deleteSectionType(id, reassignToId) {
    const suffix =
      reassignToId === undefined ? "" : `?reassignTo=${reassignToId}`;
    await apiFetchVoid(`/axonia/section-types/${id}${suffix}`, {
      method: "DELETE",
    });
  },

  async listSeries(slug, params) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/series${query({ ...params })}`,
    );
    return seriesPageSchema.parse(data);
  },

  async startSeries(slug, input: SeriesInput) {
    const data = await apiFetch<unknown>(`/axonia/communities/${slug}/series`, {
      method: "POST",
      body: JSON.stringify(input),
    });
    return seriesSchema.parse(data);
  },

  async updateSeries(id, input: SeriesInput) {
    const data = await apiFetch<unknown>(`/axonia/series/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return seriesSchema.parse(data);
  },

  async endSeries(id: number) {
    const data = await apiFetch<unknown>(`/axonia/series/${id}/end`, {
      method: "POST",
    });
    return seriesSchema.parse(data);
  },

  async listUpcoming(slug, params: PageParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/sessions${query({ ...params })}`,
    );
    return sessionPageSchema.parse(data);
  },

  async listPast(slug, params: PastSessionParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/sessions/past${query({ ...params })}`,
    );
    return sessionPageSchema.parse(data);
  },

  async recordFollowUp(sessionId, input) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/follow-up`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
    return sessionSchema.parse(data);
  },

  async cancelSession(sessionId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/cancel`,
      {
        method: "POST",
      },
    );
    return sessionSchema.parse(data);
  },

  async closeSession(sessionId, input) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/close`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
    return sessionSchema.parse(data);
  },

  async setWrapUpRelease(sessionId, input) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/wrap-up-release`,
      { method: "PUT", body: JSON.stringify(input) },
    );
    return sessionSchema.parse(data);
  },

  async setUpcomingRelease(sessionId, input) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/upcoming-release`,
      { method: "PUT", body: JSON.stringify(input) },
    );
    return sessionSchema.parse(data);
  },

  async getMessagePreview(slug, sessionId, messageType) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/sessions/${sessionId}/message-preview${query({ type: messageType })}`,
    );
    return messagePreviewSchema.parse(data);
  },

  async listAttendance(sessionId, params: PageParams) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/attendance${query({ ...params })}`,
    );
    return attendancePageSchema.parse(data);
  },

  async signalGoing(sessionId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/attendance`,
      {
        method: "POST",
      },
    );
    return attendanceSchema.parse(data);
  },

  async unsignalGoing(sessionId) {
    await apiFetchVoid(`/axonia/sessions/${sessionId}/attendance/me`, {
      method: "DELETE",
    });
  },

  async addSection(sessionId, input: SectionTemplateInput) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections`,
      { method: "POST", body: JSON.stringify(input) },
    );
    return sessionSchema.parse(data);
  },

  async updateSection(sessionId, sectionId, input: SectionTemplateInput) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}`,
      { method: "PUT", body: JSON.stringify(input) },
    );
    return sessionSchema.parse(data);
  },

  async removeSection(sessionId, sectionId) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/${sectionId}`,
      { method: "DELETE" },
    );
    return sessionSchema.parse(data);
  },

  async reorderSections(sessionId, sectionIds: number[]) {
    const data = await apiFetch<unknown>(
      `/axonia/sessions/${sessionId}/sections/order`,
      { method: "PUT", body: JSON.stringify({ sectionIds }) },
    );
    return sessionSchema.parse(data);
  },

  async getSchedule(slug, fromIso, toIso) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/schedule${query({ from: fromIso, to: toIso })}`,
    );
    return scheduleSchema.parse(data);
  },

  async getScheduleActivity(slug) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/schedule/activity`,
    );
    return scheduleActivitySchema.parse(data);
  },

  async ensureOccurrence(seriesId, occurrenceDate) {
    const data = await apiFetch<unknown>(
      `/axonia/series/${seriesId}/occurrences`,
      { method: "POST", body: JSON.stringify({ occurrenceDate }) },
    );
    return sessionSchema.parse(data);
  },

  async skipOccurrence(seriesId, occurrenceDate) {
    const data = await apiFetch<unknown>(
      `/axonia/series/${seriesId}/occurrences/skip`,
      { method: "POST", body: JSON.stringify({ occurrenceDate }) },
    );
    return sessionSchema.parse(data);
  },

  async unskipOccurrence(seriesId, occurrenceDate) {
    const data = await apiFetch<unknown>(
      `/axonia/series/${seriesId}/occurrences/unskip`,
      { method: "POST", body: JSON.stringify({ occurrenceDate }) },
    );
    return sessionSchema.parse(data);
  },

  async createOneOff(slug, input: CreateOneOffInput) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/sessions`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
    return sessionSchema.parse(data);
  },

  async updateSession(id, input: UpdateSessionInput) {
    const data = await apiFetch<unknown>(`/axonia/sessions/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return sessionSchema.parse(data);
  },

  async memberZones(slug) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${encodeURIComponent(slug)}/member-zones`,
    );
    return memberZoneListSchema.parse(data);
  },

  async getSession(id) {
    const data = await apiFetch<unknown>(`/axonia/sessions/${id}`);
    return sessionSchema.parse(data);
  },
};
