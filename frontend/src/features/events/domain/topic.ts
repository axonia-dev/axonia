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
