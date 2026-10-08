import { z } from "zod";

export const heldAreaSchema = z.object({
  tag: z.string(),
  declaredAt: z.string(),
});

const wantedAreaSchema = z.object({
  tag: z.string(),
  learners: z.number(),
  /** Who can be asked about the area in this community: the Expertise page's count. */
  canBeAsked: z.number(),
});

export const learningPageSchema = z.object({
  member: z.boolean(),
  archived: z.boolean(),
  limit: z.number(),
  mine: z.array(heldAreaSchema),
  wanted: z.array(wantedAreaSchema),
});

export const learnerSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
  declaredAt: z.string(),
});

export type HeldArea = z.infer<typeof heldAreaSchema>;
export type WantedArea = z.infer<typeof wantedAreaSchema>;
export type LearningPage = z.infer<typeof learningPageSchema>;
export type Learner = z.infer<typeof learnerSchema>;
