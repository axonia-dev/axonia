import { z } from "zod";

import {
  reactionDefinitionSchema,
  reactionEligibilitySchema,
  type ReactionRef,
  reactionTallySchema,
} from "@/features/recognition/domain/reaction";
import type { ReactionGateway } from "@/features/recognition/domain/reaction-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

const talliesSchema = z.array(reactionTallySchema);
const definitionsSchema = z.array(reactionDefinitionSchema);

const BASE = "/axonia/recognition";

/** Only the arm that is set travels; a null one would reach the controller as the string "null". */
function refParams(ref: ReactionRef) {
  return ref.kind === "EMOJI"
    ? { kind: ref.kind, emoji: ref.emoji }
    : { kind: ref.kind, definitionId: ref.definitionId };
}

export const httpReactionGateway: ReactionGateway = {
  async tally(key) {
    return talliesSchema.parse(
      await apiFetch<unknown>(`${BASE}/reactions${query({ ...key })}`),
    );
  },

  async react(key, ref) {
    return reactionTallySchema.parse(
      await apiFetch<unknown>(`${BASE}/reactions`, {
        method: "POST",
        body: JSON.stringify({ ...key, ...refParams(ref) }),
      }),
    );
  },

  async unreact(key, ref) {
    await apiFetchVoid(
      `${BASE}/reactions${query({ ...key, ...refParams(ref) })}`,
      { method: "DELETE" },
    );
  },

  async listDefinitions() {
    return definitionsSchema.parse(
      await apiFetch<unknown>(`${BASE}/reaction-definitions`),
    );
  },

  async listAllDefinitions() {
    return definitionsSchema.parse(
      await apiFetch<unknown>(`${BASE}/reaction-definitions/all`),
    );
  },

  async listMyProposals() {
    return definitionsSchema.parse(
      await apiFetch<unknown>(`${BASE}/reaction-definitions/mine`),
    );
  },

  async eligibility() {
    return reactionEligibilitySchema.parse(
      await apiFetch<unknown>(`${BASE}/reaction-eligibility`),
    );
  },

  async propose(name, file) {
    // No Content-Type here: the browser must set multipart's boundary, and a header passed at a call
    // site is appended rather than replaced, which arrives as "application/json, ..." and 415s.
    const body = new FormData();
    body.append("name", name);
    body.append("file", file);
    return reactionDefinitionSchema.parse(
      await apiFetch<unknown>(`${BASE}/reaction-definitions`, {
        method: "POST",
        body,
      }),
    );
  },

  async approve(id) {
    return reactionDefinitionSchema.parse(
      await apiFetch<unknown>(`${BASE}/reaction-definitions/${id}/approve`, {
        method: "POST",
      }),
    );
  },

  async remove(id) {
    await apiFetchVoid(`${BASE}/reaction-definitions/${id}`, {
      method: "DELETE",
    });
  },
};
