import { z } from "zod";

const contributionTypeSchema = z.enum(["SLOT_PRESENTED", "ARTIFACT_CAPTURED"]);
export type ContributionType = z.infer<typeof contributionTypeSchema>;

export const loveSchema = z.object({
  id: z.number(),
  contributionType: contributionTypeSchema,
  contributionId: z.number(),
  communityId: z.number(),
  loverUserId: z.string(),
  createdAt: z.string(),
});
export type Love = z.infer<typeof loveSchema>;

export const loveSummarySchema = z.object({
  count: z.number(),
  viewerHasLoved: z.boolean(),
});
export type LoveSummary = z.infer<typeof loveSummarySchema>;

export const recognizedContributionSchema = z.object({
  contributionType: contributionTypeSchema,
  contributionId: z.number(),
  communityId: z.number(),
  communitySlug: z.string().nullable(),
  communityName: z.string().nullable().default(null),
  title: z.string(),
  occurredAt: z.string(),
  loveCount: z.number(),
  /** Session the presented slot belongs to — deep-link target for SLOT_PRESENTED. */
  sessionId: z.number().nullable().default(null),
  /** Section type (slots) or artifact type (artifacts): id/label/icon/accent for the badge. */
  typeId: z.number().nullable().default(null),
  typeLabel: z.string().nullable().default(null),
  typeIcon: z.string().nullable().default(null),
  typeAccentColor: z.string().nullable().default(null),
});
export type RecognizedContribution = z.infer<
  typeof recognizedContributionSchema
>;

export interface ContributionKey {
  contributionType: ContributionType;
  contributionId: number;
}
