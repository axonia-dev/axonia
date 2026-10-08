import { z } from "zod";

export const topicCountsSchema = z.object({
  proposed: z.number(),
  scheduled: z.number(),
  done: z.number(),
});

export type TopicCounts = z.infer<typeof topicCountsSchema>;
