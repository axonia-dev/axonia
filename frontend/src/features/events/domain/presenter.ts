import { z } from "zod";

import { sessionSchema } from "@/features/events/domain/session";

/**
 * Which presenter references the backend could place, and which it could not. A name that matches no member, or
 * more than one, is not an error: the slot is created or left as it was, and the caller is told, so it can ask a
 * human rather than guess.
 */
const presenterResolutionSchema = z.object({
  assigned: z.array(z.string()),
  unmatched: z.array(z.string()),
  ambiguous: z.array(z.string()),
});

export const recordedSlotSchema = z.object({
  session: sessionSchema,
  slotId: z.number(),
  presenters: presenterResolutionSchema,
});
export type RecordedSlot = z.infer<typeof recordedSlotSchema>;

export const presenterAssignmentSchema = z.object({
  session: sessionSchema,
  assigned: z.boolean(),
  reason: z.enum(["NOT_FOUND", "AMBIGUOUS"]).nullable(),
});
export type PresenterAssignment = z.infer<typeof presenterAssignmentSchema>;
