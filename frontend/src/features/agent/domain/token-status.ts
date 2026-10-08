import type { StatusTone } from "@/components/ui/StatusChip";

import type { PersonalTokenStatus } from "./personal-access-token";

export interface TokenStatusView {
  tone: StatusTone;
  label: string;
}

/**
 * Expired is neutral, not a warning: a key lapsing on the date it was given is the control
 * working, and colouring it as a fault would compete with the one status that is a fault.
 */
const views: Record<PersonalTokenStatus, TokenStatusView> = {
  ACTIVE: { tone: "success", label: "Active" },
  REVOKED: { tone: "danger", label: "Revoked" },
  EXPIRED: { tone: "neutral", label: "Expired" },
};

export function tokenStatusView(status: PersonalTokenStatus): TokenStatusView {
  return views[status];
}
