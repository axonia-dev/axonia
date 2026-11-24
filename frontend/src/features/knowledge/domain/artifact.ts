import { z } from "zod";

const artifactStatusSchema = z.enum(["ACTIVE", "ARCHIVED"]);

/** `label` is frozen at capture; `seriesTitle` and `heldOn` are read live, and are null once the session is gone. */
const provenanceSchema = z.object({
  sessionId: z.number(),
  segmentId: z.number().nullable(),
  label: z.string().nullable(),
  seriesTitle: z.string().nullable(),
  heldOn: z.string().nullable(),
});

export type Provenance = z.infer<typeof provenanceSchema>;

export const artifactSchema = z.object({
  id: z.number(),
  communityId: z.number(),
  typeId: z.number(),
  type: z.string(),
  title: z.string(),
  description: z.string(),
  url: z.string(),
  tags: z.array(z.string()),
  contributorUserId: z.string(),
  loveCount: z.number(),
  provenance: provenanceSchema.nullable(),
  status: artifactStatusSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type Artifact = z.infer<typeof artifactSchema>;

export function isArchived(artifact: Artifact): boolean {
  return artifact.status === "ARCHIVED";
}
