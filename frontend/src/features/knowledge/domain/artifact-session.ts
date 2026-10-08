import { z } from "zod";

/**
 * A session that has at least one artifact attributed to it (via provenance or
 * an attachment), with the count of those artifacts. Powers the Session filter
 * so empty sessions never appear as options.
 */
export const artifactSessionSchema = z.object({
  sessionId: z.number(),
  count: z.number(),
});

export type ArtifactSession = z.infer<typeof artifactSessionSchema>;
