import { z } from "zod";

export const sectionTypeSchema = z.object({
  id: z.number(),
  label: z.string(),
  expectsPresenter: z.boolean(),
  icon: z.string().nullable(),
  accentColor: z.string().nullable(),
});

export type SectionType = z.infer<typeof sectionTypeSchema>;
