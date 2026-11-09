import { z } from "zod";

const topicKindSchema = z.enum(["REQUEST", "OFFER"]);
export type TopicKind = z.infer<typeof topicKindSchema>;

const topicStatusSchema = z.enum(["PROPOSED", "SCHEDULED", "DONE"]);

const scheduledForSchema = z.object({
  sessionId: z.number(),
  scheduledAt: z.string(),
  scheduleLabel: z.string(),
});

export const topicSchema = z.object({
  id: z.number(),
  communityId: z.number(),
  kind: topicKindSchema,
  title: z.string(),
  description: z.string(),
  tags: z.array(z.string()).default([]),
  proposerUserId: z.string(),
  proposerDisplayName: z.string(),
  speakerUserIds: z.array(z.string()).default([]),
  canManageSpeakers: z.boolean().default(false),
  canClose: z.boolean().default(false),
  canReopen: z.boolean().default(false),
  voteCount: z.number(),
  hasVoted: z.boolean(),
  canEdit: z.boolean(),
  canDelete: z.boolean(),
  status: topicStatusSchema,
  scheduledFor: scheduledForSchema.nullish().transform((v) => v ?? null),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type Topic = z.infer<typeof topicSchema>;

export const speakerAssignmentSchema = z.object({
  topic: topicSchema,
  assigned: z.boolean(),
  reason: z
    .enum(["NOT_FOUND", "AMBIGUOUS"])
    .nullish()
    .transform((v) => v ?? null),
});
export type SpeakerAssignment = z.infer<typeof speakerAssignmentSchema>;

/** Everyone a list of topics draws: each creator and each speaker, once. */
export function topicPeopleIds(topics: Topic[]): string[] {
  const ids = topics.flatMap((topic) => [
    topic.proposerUserId,
    ...topic.speakerUserIds,
  ]);
  return [...new Set(ids)].sort((a, b) => a.localeCompare(b));
}
