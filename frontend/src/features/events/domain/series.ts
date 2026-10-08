import { z } from "zod";

import { meetingPlaceSchema } from "@/features/events/domain/meeting-place";

const cadenceUnitSchema = z.enum(["DAY", "WEEK", "MONTH", "YEAR"]);
export type CadenceUnit = z.infer<typeof cadenceUnitSchema>;

const cadenceSchema = z.object({
  count: z.number().int().min(1).max(999),
  unit: cadenceUnitSchema,
});
export type Cadence = z.infer<typeof cadenceSchema>;

const seriesStatusSchema = z.enum(["ACTIVE", "PAUSED", "ENDED"]);

const sectionTemplateSchema = z.object({
  sectionTypeId: z.number(),
  title: z.string().nullable(),
  durationMinutes: z.number().nullable(),
});

export const seriesSchema = z.object({
  id: z.number(),
  communityId: z.number(),
  title: z.string(),
  cadence: cadenceSchema,
  startsOn: z.string(),
  preferredTime: z.string().nullable(),
  timeZone: z.string(),
  durationMinutes: z.number().nullable(),
  /** The standing place every occurrence of this series meets in unless it sets its own. */
  place: meetingPlaceSchema,
  status: seriesStatusSchema,
  defaultStructure: z.array(sectionTemplateSchema),
  nextSuggestedAt: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type Series = z.infer<typeof seriesSchema>;
