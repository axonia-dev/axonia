import { useManagementPolicyQuery } from "@/features/community/api/use-management-policy";
import { useMyMembership } from "@/features/community/api/use-membership";
import { curated } from "@/features/community/domain/management-policy";

export interface ManagementAccess {
  editCommunity: boolean;
  lookAndFeel: boolean;
  members: boolean;
  contributionPolicy: boolean;
  archive: boolean;
  managementPolicy: boolean;
  curation: boolean;
  health: boolean;
  invite: boolean;
  communication: boolean;
  credit: boolean;
  any: boolean;
  /** Membership or policy has not resolved yet: a caller gating on one axis (e.g. `credit`)
   *  should wait rather than briefly act on the closed default. */
  isLoading: boolean;
}

/** What the current user may manage in this community: an organizer may do all;
 *  a member may do each section its management-policy axis opens. UX-only: the
 *  backend enforces the same rule. */
export function useManagementAccess(slug: string): ManagementAccess {
  const membershipQuery = useMyMembership(slug);
  const policyQuery = useManagementPolicyQuery(slug);
  const membership = membershipQuery.data;
  const policy = policyQuery.data ?? curated();
  const organizer = membership?.role === "ORGANIZER";
  const member = membership?.role === "MEMBER";
  const open = (axis: boolean) => organizer || (member && axis);

  const access = {
    editCommunity: open(policy.canEditCommunity),
    lookAndFeel: open(policy.canManageLookAndFeel),
    members: open(policy.canManageMembers),
    contributionPolicy: open(policy.canManageContributionPolicy),
    archive: open(policy.canArchiveCommunity),
    managementPolicy: organizer,
    curation: open(policy.canCurateTags),
    health: open(policy.canViewHealth),
    invite: open(policy.canInviteMembers),
    communication: open(policy.canManageCommunication),
    credit: open(policy.canCreditContributions),
  };
  return {
    ...access,
    any: Object.values(access).some(Boolean),
    isLoading: membershipQuery.isLoading || policyQuery.isLoading,
  };
}
