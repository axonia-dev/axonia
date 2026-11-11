import { z } from "zod";

const openFaceSchema = z.object({
  userId: z.string(),
  displayName: z.string().nullable(),
  avatarUrl: z.string().nullable(),
});

export const heldAreaSchema = z.object({
  tag: z.string(),
  declaredAt: z.string(),
  /** Up to four of the people open to questions on the area in this community, the member excluded. */
  open: z.array(openFaceSchema).default([]),
  /** How many are open, however many faces `open` carries. */
  openCount: z.number().default(0),
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
