import { z } from "zod";

/**
 * A session that has at least one artifact attributed to it (via provenance or
 * an attachment), with the count of those artifacts. Powers the Session filter
 * so empty sessions never appear as options. The server labels and orders them,
 * newest held first; `name` and `heldOn` are null for a session that is gone.
 */
export const artifactSessionSchema = z.object({
  sessionId: z.number(),
  count: z.number(),
  name: z.string().nullable(),
  heldOn: z.string().nullable(),
});

export type ArtifactSession = z.infer<typeof artifactSessionSchema>;
