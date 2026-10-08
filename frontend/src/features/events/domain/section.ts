import { z } from "zod";

import { slotSchema } from "@/features/events/domain/slot";

export const sectionSchema = z.object({
  id: z.number(),
  sessionId: z.number(),
  position: z.number(),
  sectionTypeId: z.number().nullable(),
  sectionType: z.string().nullable(),
  sectionTypeIcon: z.string().nullable(),
  sectionTypeAccentColor: z.string().nullable(),
  title: z.string().nullable(),
  durationMinutes: z.number().nullable(),
  slots: z.array(slotSchema),
});

export type Section = z.infer<typeof sectionSchema>;

export function isRoundTable(section: Pick<Section, "slots">): boolean {
  return section.slots.length === 0;
}

export function sectionUsedMinutes(section: Section): number {
  return section.slots.reduce((sum, slot) => sum + slot.durationMinutes, 0);
}

/** A section's own accent, or the shared default when its type carries none. */
export function sectionAccentColor(
  section: Pick<Section, "sectionTypeAccentColor">,
): string {
  return section.sectionTypeAccentColor ?? "var(--primary)";
}

/** What to call a section on screen: its type, else its own title, else a plain fallback. */
export function sectionLabel(
  section: Pick<Section, "sectionType" | "title">,
): string {
  return section.sectionType ?? section.title ?? "Section";
}
