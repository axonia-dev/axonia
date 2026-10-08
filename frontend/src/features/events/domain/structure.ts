import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";

export function emptyTemplate(sectionTypeId: number): SectionTemplateInput {
  return { sectionTypeId, title: null, durationMinutes: null };
}

export function totalMinutes(list: readonly SectionTemplateInput[]): number {
  return list.reduce((sum, t) => sum + (t.durationMinutes ?? 0), 0);
}
