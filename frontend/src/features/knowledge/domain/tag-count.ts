import { z } from "zod";

/** A tag and how many recent artifacts carry it, counted by the server over the whole library. */
export const tagCountSchema = z.object({
  tag: z.string(),
  count: z.number(),
});

export type TagCount = z.infer<typeof tagCountSchema>;
