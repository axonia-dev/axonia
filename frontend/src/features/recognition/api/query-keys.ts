import type { ContributionKey } from "@/features/recognition/domain/recognition";

export const recognitionKeys = {
  all: ["recognition"] as const,
  summary: (key: ContributionKey) =>
    [
      ...recognitionKeys.all,
      "summary",
      key.contributionType,
      key.contributionId,
    ] as const,
  myContributions: () => [...recognitionKeys.all, "me"] as const,
  /** Every contribution's tallies at once: the prefix a catalog change has to reach, since
   *  approving or removing a definition changes what any mounted bar should be drawing. */
  reactionsAll: () => [...recognitionKeys.all, "reactions"] as const,
  reactions: (key: ContributionKey) =>
    [
      ...recognitionKeys.reactionsAll(),
      key.contributionType,
      key.contributionId,
    ] as const,
  reactionDefinitions: () => [...recognitionKeys.all, "definitions"] as const,
  allReactionDefinitions: () =>
    [...recognitionKeys.all, "definitions", "all"] as const,
  myReactionProposals: () =>
    [...recognitionKeys.all, "definitions", "mine"] as const,
  reactionEligibility: () => [...recognitionKeys.all, "eligibility"] as const,
};
