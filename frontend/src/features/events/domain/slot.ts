import { z } from "zod";

export const slotSchema = z.object({
  id: z.number(),
  sectionId: z.number(),
  position: z.number(),
  presenterUserIds: z.array(z.string()),
  topicId: z.number().nullable(),
  title: z.string().nullable(),
  notes: z.string().nullable(),
  durationMinutes: z.number().int().positive(),
  /**
   * What the talk was about, when it was never proposed. A slot filled from a backlog topic takes
   * its subject from that topic and carries no tags of its own: a slot has a topic or its own
   * tags, never both. Sorted alphabetically by the backend.
   */
  tags: z.array(z.string()).default([]),
});

export type Slot = z.infer<typeof slotSchema>;

export function isOpen(slot: Slot): boolean {
  return slot.presenterUserIds.length === 0;
}
