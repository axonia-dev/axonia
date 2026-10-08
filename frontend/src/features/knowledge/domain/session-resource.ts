import { z } from "zod";

import { artifactSchema } from "@/features/knowledge/domain/artifact";

export const sessionResourceSchema = z.object({
  attachmentId: z.number().nullable(),
  artifact: artifactSchema,
  segmentId: z.number().nullable(),
  attachedByUserId: z.string().nullable(),
});

export type SessionResource = z.infer<typeof sessionResourceSchema>;

export const attachmentSchema = z.object({
  id: z.number(),
  artifactId: z.number(),
  sessionId: z.number(),
  segmentId: z.number().nullable(),
  attachedByUserId: z.string(),
  createdAt: z.string(),
});

export type Attachment = z.infer<typeof attachmentSchema>;

export interface AttachResourceInput {
  segmentId?: number;
  artifactId: number;
}

export interface DetachResourceInput {
  artifactId: number;
  segmentId: number | null;
}

/**
 * Who may take a resource off a talk. An attached row answers to whoever attached it; a row
 * shown only because the artifact came from this session answers to its contributor. An
 * organizer may remove either.
 */
export function canDetachResource(
  resource: SessionResource,
  canManage: boolean,
  userId: string | undefined,
): boolean {
  if (canManage) return true;
  const answerableTo =
    resource.attachmentId === null
      ? resource.artifact.contributorUserId
      : resource.attachedByUserId;
  return userId !== undefined && answerableTo === userId;
}
