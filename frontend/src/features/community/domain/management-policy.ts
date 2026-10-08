import { z } from "zod";

export const managementPolicySchema = z.object({
  canEditCommunity: z.boolean(),
  canManageLookAndFeel: z.boolean(),
  canManageMembers: z.boolean(),
  canManageContributionPolicy: z.boolean(),
  canArchiveCommunity: z.boolean(),
  canCurateTags: z.boolean(),
  canViewHealth: z.boolean(),
  canInviteMembers: z.boolean(),
  canManageCommunication: z.boolean(),
  canCreditContributions: z.boolean(),
});

export type ManagementPolicy = z.infer<typeof managementPolicySchema>;

/** The default stance for every community: organizers-only on every section. */
export function curated(): ManagementPolicy {
  return {
    canEditCommunity: false,
    canManageLookAndFeel: false,
    canManageMembers: false,
    canManageContributionPolicy: false,
    canArchiveCommunity: false,
    canCurateTags: false,
    canViewHealth: false,
    canInviteMembers: false,
    canManageCommunication: false,
    canCreditContributions: false,
  };
}
