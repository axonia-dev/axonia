import type { MentoringRole } from "@/features/mentoring/domain/mentoring";

/** A mentee asks a mentor; a mentor offers to a mentee. */
export function proposalWording(asRole: MentoringRole) {
  return asRole === "MENTEE"
    ? {
        label: "Ask for mentoring",
        named: (name: string) => `Ask ${name} for mentoring`,
      }
    : {
        label: "Offer mentoring",
        named: (name: string) => `Offer mentoring to ${name}`,
      };
}

export const SHOWN_ONCE_ACCEPTED =
  "Once accepted, the pairing shows on the community’s Mentoring page.";
