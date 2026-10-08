import { z } from "zod";

/** A grouping of canonical tags a leader named. Stored, unlike a neighbourhood, which the map derives on every render. */
export const namedAreaSchema = z.object({
  id: z.number(),
  name: z.string(),
  tags: z.array(z.string()),
});

/** The width of the stored column. */
export const MAX_NAMED_AREA_NAME_LENGTH = 80;

export type NamedArea = z.infer<typeof namedAreaSchema>;
