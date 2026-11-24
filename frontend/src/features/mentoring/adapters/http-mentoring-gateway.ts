import { z } from "zod";

import {
  availabilitySchema,
  journalSchema,
  mentorFaceSchema,
  mentoringPageSchema,
  myMentoringSchema,
  pairingSummarySchema,
  plannedMeetingSchema,
  presenceSchema,
  proposalSchema,
  wordsSchema,
} from "@/features/mentoring/domain/mentoring";
import type { MentoringGateway } from "@/features/mentoring/domain/mentoring-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { pageSchema } from "@/lib/page";

const wordsListSchema = z.array(wordsSchema);

const MENTORS_PAGE_SIZE = 10;

const ROOT = "/axonia/mentoring";

function guild(slug: string): string {
  return `/axonia/communities/${slug}/mentoring`;
}

function pairing(id: number): string {
  return `${ROOT}/pairings/${id}`;
}

type Init = NonNullable<Parameters<typeof apiFetchVoid>[1]>;

function json(method: string, body?: unknown): Init {
  return body === undefined
    ? { method }
    : { method, body: JSON.stringify(body) };
}

export const httpMentoringGateway: MentoringGateway = {
  async page(slug) {
    return mentoringPageSchema.parse(await apiFetch<unknown>(guild(slug)));
  },

  async setAvailability(slug, availability) {
    const data = await apiFetch<unknown>(
      `${guild(slug)}/availability`,
      json("PUT", availability),
    );
    return availabilitySchema.parse(data);
  },

  async propose(slug, proposal) {
    const data = await apiFetch<unknown>(
      `${guild(slug)}/proposals`,
      json("POST", proposal),
    );
    return proposalSchema.parse(data);
  },

  async mentors(slug, page) {
    const data = await apiFetch<unknown>(
      `${guild(slug)}/mentors?page=${page}&size=${MENTORS_PAGE_SIZE}`,
    );
    return pageSchema(mentorFaceSchema).parse(data);
  },

  setListing: (slug, listed) =>
    apiFetchVoid(`${guild(slug)}/listing`, json("PUT", { listed })),

  async mine() {
    return myMentoringSchema.parse(await apiFetch<unknown>(`${ROOT}/me`));
  },

  async presence() {
    return presenceSchema.parse(await apiFetch<unknown>(`${ROOT}/me/presence`));
  },

  async answer(id, answer) {
    const path = `${ROOT}/proposals/${id}/${answer}`;
    if (answer !== "accept") {
      await apiFetchVoid(path, json("POST"));
      return null;
    }
    return pairingSummarySchema.parse(
      await apiFetch<unknown>(path, json("POST")),
    );
  },

  async journal(id) {
    return journalSchema.parse(await apiFetch<unknown>(pairing(id)));
  },

  end: (id) => apiFetchVoid(`${pairing(id)}/end`, json("POST")),

  addTopic: (id, topic) =>
    apiFetchVoid(`${pairing(id)}/topics`, json("POST", topic)),
  updateTopic: (id, topicId, topic) =>
    apiFetchVoid(`${pairing(id)}/topics/${topicId}`, json("PATCH", topic)),
  removeTopic: (id, topicId) =>
    apiFetchVoid(`${pairing(id)}/topics/${topicId}`, json("DELETE")),
  placeTopic: (id, topicId, placement) =>
    apiFetchVoid(
      `${pairing(id)}/topics/${topicId}/placement`,
      json("PUT", placement),
    ),

  addNote: (id, note) =>
    apiFetchVoid(`${pairing(id)}/notes`, json("POST", note)),
  updateNote: (id, noteId, note) =>
    apiFetchVoid(`${pairing(id)}/notes/${noteId}`, json("PATCH", note)),
  removeNote: (id, noteId) =>
    apiFetchVoid(`${pairing(id)}/notes/${noteId}`, json("DELETE")),

  addPointer: (id, pointer) =>
    apiFetchVoid(`${pairing(id)}/pointers`, json("POST", pointer)),
  removePointer: (id, pointerId) =>
    apiFetchVoid(`${pairing(id)}/pointers/${pointerId}`, json("DELETE")),

  writeWords: (id, body) =>
    apiFetchVoid(`${pairing(id)}/words`, json("PUT", { body })),
  takeBackWords: (id) => apiFetchVoid(`${pairing(id)}/words`, json("DELETE")),
  openWords: (id) => apiFetchVoid(`${pairing(id)}/words/open`, json("POST")),

  async planMeeting(id, input) {
    const data = await apiFetch<unknown>(
      `${pairing(id)}/meetings`,
      json("POST", input),
    );
    return plannedMeetingSchema.parse(data);
  },
  changeMeeting: (id, meetingId, input) =>
    apiFetchVoid(`${pairing(id)}/meetings/${meetingId}`, json("PUT", input)),
  removeMeeting: (id, meetingId) =>
    apiFetchVoid(`${pairing(id)}/meetings/${meetingId}`, json("DELETE")),

  answerShowing: (id, answer) =>
    apiFetchVoid(`${pairing(id)}/showing`, json("PUT", { answer })),

  async myWords() {
    return wordsListSchema.parse(await apiFetch<unknown>(`${ROOT}/me/words`));
  },
};
