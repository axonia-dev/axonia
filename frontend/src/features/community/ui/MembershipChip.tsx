import { Check } from "lucide-react";

import type { CommunityRole } from "@/features/community/domain/membership";

const LABELS: Record<CommunityRole, string> = {
  ORGANIZER: "Organizer",
  MEMBER: "Member",
};

/**
 * The hero's quiet statement that you belong. It replaces the Leave button that
 * used to sit in the card's call-to-action slot: membership is a state, not an
 * invitation to act. Tinted in the community's own accent so belonging reads in
 * this community's colour, and it names the role, which appears nowhere else.
 */
export function MembershipChip({ role }: Readonly<{ role: CommunityRole }>) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-[color-mix(in_srgb,var(--community-accent,var(--primary))_13%,var(--surface))] px-3 py-1 text-xs font-semibold whitespace-nowrap text-[color-mix(in_srgb,var(--community-accent,var(--primary))_80%,var(--foreground))]">
      <Check size={14} aria-hidden />
      {LABELS[role]}
    </span>
  );
}
