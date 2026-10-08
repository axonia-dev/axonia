import { z } from "zod";

export const contributionPolicySchema = z.object({
  canStartSeries: z.boolean(),
  canEditStructure: z.boolean(),
  canQueueTopics: z.boolean(),
  canManageSchedule: z.boolean(),
});

export type ContributionPolicy = z.infer<typeof contributionPolicySchema>;

/** The default stance for every community: organizers-only on all axes. */
export function curated(): ContributionPolicy {
  return {
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  };
}
