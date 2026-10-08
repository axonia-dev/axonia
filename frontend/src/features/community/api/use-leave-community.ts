import {
  clearStoredActiveOrganizationId,
  organizationlessScope,
} from "@/auth/active-organization";
import { useAuth } from "@/auth/session";
import { notifyError } from "@/components/ui/toast";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useLeave } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";

export interface LeaveCommunity {
  /** True once the organization kind is known. Until then Leave must not be offered:
   *  a hub exit would be skipped and the org-scoped token left stale. */
  isReady: boolean;
  /** In a hub, leaving the one community IS leaving the tenant, so it must be confirmed. */
  isHub: boolean;
  isPending: boolean;
  error: unknown;
  leave: (onDone?: () => void) => void;
}

/**
 * Leaving, and everything leaving means. In an organization it is instant and a
 * failure surfaces as a toast, because the menu that fired it has already closed.
 * In a hub the caller must confirm first, and a successful leave ends the tenant
 * membership too: the org-scoped token is now stale, so drop the active
 * organization and re-authorize org-less, the same exit as deleting the hub.
 */
export function useLeaveCommunity(slug: string): LeaveCommunity {
  const leave = useLeave(slug);
  const auth = useAuth();
  const { data: organization } = useCurrentOrganization();
  const isHub = organization?.kind === "HUB";

  function exitHub() {
    clearStoredActiveOrganizationId();
    // Back to the root, never to the community just left, which no longer answers.
    void auth.signinRedirect({
      scope: organizationlessScope(),
      state: { returnTo: "/" },
    });
  }

  return {
    isReady: organization !== undefined,
    isHub,
    isPending: leave.isPending,
    error: leave.error,
    leave: (onDone?: () => void) =>
      leave.mutate(undefined, {
        onSuccess: () => {
          onDone?.();
          if (isHub) {
            exitHub();
          }
        },
        // The hub path renders its failure inside the open dialog; the org path
        // has no surface left to render into once the menu closes.
        onError: (error) => {
          if (!isHub) {
            notifyError(communityErrorMessage(error));
          }
        },
      }),
  };
}
