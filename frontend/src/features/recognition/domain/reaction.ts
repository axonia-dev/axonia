import { z } from "zod";

const reactionKindSchema = z.enum(["EMOJI", "CUSTOM"]);
type ReactionKind = z.infer<typeof reactionKindSchema>;

export const reactionTallySchema = z.object({
  kind: reactionKindSchema,
  emoji: z.string().nullable(),
  definitionId: z.number().nullable(),
  name: z.string().nullable(),
  imageUrl: z.string().nullable(),
  count: z.number(),
  viewerHasReacted: z.boolean(),
});
export type ReactionTally = z.infer<typeof reactionTallySchema>;

export const reactionDefinitionSchema = z.object({
  id: z.number(),
  name: z.string(),
  imageUrl: z.string(),
  pending: z.boolean(),
  proposedByUserId: z.string().nullable().default(null),
  usageCount: z.number().default(0),
});
export type ReactionDefinition = z.infer<typeof reactionDefinitionSchema>;

export const reactionEligibilitySchema = z.object({
  mayPropose: z.boolean(),
  openProposals: z.number(),
  openProposalLimit: z.number(),
  approvedLimit: z.number(),
});
export type ReactionEligibility = z.infer<typeof reactionEligibilitySchema>;

/** A reaction's identity, with exactly one arm set. */
export interface ReactionRef {
  kind: ReactionKind;
  emoji?: string;
  definitionId?: number;
}

export function refOf(tally: ReactionTally): ReactionRef {
  return tally.kind === "EMOJI"
    ? { kind: "EMOJI", emoji: tally.emoji ?? "" }
    : { kind: "CUSTOM", definitionId: tally.definitionId ?? 0 };
}

/** Stable string form, for a React key and for cache-key equality. */
export function refKey(ref: ReactionRef): string {
  return ref.kind === "EMOJI" ? `e:${ref.emoji}` : `c:${ref.definitionId}`;
}
