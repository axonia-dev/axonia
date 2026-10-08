import type { ContributionPolicy } from "@/features/community/domain/contribution-policy";
import type { ManagementPolicy } from "@/features/community/domain/management-policy";

export const CADENCE_AXES = [
  { key: "canStartSeries", label: "Start a series" },
  { key: "canEditStructure", label: "Edit a series" },
  { key: "canQueueTopics", label: "Queue backlog topics" },
  { key: "canManageSchedule", label: "Manage the schedule" },
] as const satisfies ReadonlyArray<{
  key: keyof ContributionPolicy;
  label: string;
}>;

export const IDENTITY_AXES = [
  { key: "canEditCommunity", label: "Edit the community" },
  { key: "canManageLookAndFeel", label: "Manage look & feel" },
] as const satisfies ReadonlyArray<{
  key: keyof ManagementPolicy;
  label: string;
}>;

const PEOPLE_AXES = [
  { key: "canManageMembers", label: "Manage members & roles" },
  { key: "canCurateTags", label: "Curate the expertise taxonomy" },
  { key: "canCreditContributions", label: "Credit contributions" },
  { key: "canViewHealth", label: "View community health" },
] as const satisfies ReadonlyArray<{
  key: keyof ManagementPolicy;
  label: string;
}>;

/**
 * Everything about how this community reaches its people: the settings page that decides whether
 * session mail goes out at all, and an individual session's own reminder and wrap-up.
 *
 * Its own area rather than the danger zone, which holds only what escalates or destroys - the
 * self-referential policy switch, and archiving. Turning communication over to a member neither:
 * the worst outcome is that session mail stops, which is one switch back. It shares its name with
 * the `Communication` group on the organization roles page, so the product's two governance
 * surfaces name the same power the same way.
 */
export const COMMUNICATION_AXES = [
  { key: "canManageCommunication", label: "Manage communication" },
] as const satisfies ReadonlyArray<{
  key: keyof ManagementPolicy;
  label: string;
}>;

export const DANGER_AXES = [
  {
    key: "canManageContributionPolicy",
    label: "Manage the community policy",
  },
  { key: "canArchiveCommunity", label: "Archive the community" },
] as const satisfies ReadonlyArray<{
  key: keyof ManagementPolicy;
  label: string;
}>;

// Invites exist only in self-serve communities: enterprise stores the column
// harmlessly but never shows or enforces it, so the row renders hub-kind only.
const INVITE_AXIS = {
  key: "canInviteMembers",
  label: "Invite people",
} as const satisfies { key: keyof ManagementPolicy; label: string };

export type ManagementAxes = ReadonlyArray<{
  key: keyof ManagementPolicy;
  label: string;
}>;

export function peopleAxes(isHub: boolean): ManagementAxes {
  return isHub ? [...PEOPLE_AXES, INVITE_AXIS] : PEOPLE_AXES;
}

interface CountOpenInput {
  canContribute: boolean;
  contribution: ContributionPolicy;
  canManage: boolean;
  management: ManagementPolicy;
  managementAxes: ManagementAxes;
}

export function countOpen({
  canContribute,
  contribution,
  canManage,
  management,
  managementAxes,
}: Readonly<CountOpenInput>): { openCount: number; axisCount: number } {
  const contributionOpen = canContribute
    ? CADENCE_AXES.filter(({ key }) => contribution[key]).length
    : 0;
  const managementOpen = canManage
    ? managementAxes.filter(({ key }) => management[key]).length
    : 0;
  return {
    openCount: contributionOpen + managementOpen,
    axisCount:
      (canContribute ? CADENCE_AXES.length : 0) +
      (canManage ? managementAxes.length : 0),
  };
}
