import { CircleOff, CircleSlash } from "lucide-react";

import { signOut } from "@/auth/account-state";
import {
  clearStoredActiveOrganizationId,
  markAutoEnterAttempted,
  organizationlessScope,
} from "@/auth/active-organization";
import { useIsDelegated } from "@/auth/delegated-session";
import { useAuth } from "@/auth/session";
import type { OrganizationOfflineReason } from "@/auth/token";
import { PreviewFrame } from "@/components/patterns/PreviewFrame";
import { Button } from "@/components/ui/Button";

/**
 * What a member sees when their Organization stops serving. It is a terminal state for that
 * Organization, not for the person: the active Organization rides in the startup OIDC scope and is
 * re-requested on every reload and silent renew, so without a way to drop it a member who also
 * belongs to a healthy Organization would be locked out of that one too.
 *
 * One component, two copies, because the two states differ only in their words and in whether
 * waiting can help. Suspension is recoverable and somebody can explain it, so it is warning-toned
 * and offers a retry. Closure is neither, so it says nothing about temporary and nobody to ask,
 * and the only way on is into another Organization.
 *
 * A delegated tab gets the explanation and no actions. The frame around it carries the only exit
 * that makes sense there, and signing out of a session that is not the person's own is not it.
 */

interface Copy {
  heading: string;
  body: string;
}

const COPY: Readonly<Record<OrganizationOfflineReason, Copy>> = {
  SUSPENDED: {
    heading: "This organization’s account is suspended",
    body: "This organization’s axonia account is temporarily offline. Nothing has been deleted. Contact an administrator to find out more.",
  },
  CLOSED: {
    heading: "This organization’s account is closed",
    body: "This organization’s axonia account has been closed and its data removed. This is permanent, and there is nothing here to come back to.",
  },
};

interface OrganizationOfflineProps {
  reason: OrganizationOfflineReason;
  onRetry: () => void;
}

export function OrganizationOffline({
  reason,
  onRetry,
}: Readonly<OrganizationOfflineProps>) {
  const auth = useAuth();
  const delegated = useIsDelegated();
  const suspended = reason === "SUSPENDED";
  const copy = suspended ? COPY.SUSPENDED : COPY.CLOSED;
  const Mark = suspended ? CircleSlash : CircleOff;

  function chooseAnother() {
    clearStoredActiveOrganizationId();
    // Claim the lobby's one-shot auto-enter before redirecting, or a member whose only
    // Organization is this one is sent straight back here instead of landing in the lobby.
    markAutoEnterAttempted();
    // Back to the root rather than the page that went offline, which would only say so again.
    void auth.signinRedirect({
      scope: organizationlessScope(),
      state: { returnTo: "/" },
    });
  }

  return (
    <PreviewFrame fill>
      <div className="flex flex-col items-start gap-3">
        <h1 className="font-display text-foreground flex items-start gap-2 text-2xl font-bold">
          <Mark
            aria-hidden
            className={`mt-1 size-6 shrink-0 ${suspended ? "text-warning" : "text-danger"}`}
          />
          {copy.heading}
        </h1>
        <p className="text-muted-foreground text-sm">{copy.body}</p>
        {!delegated && (
          <div className="flex flex-wrap gap-2 pt-1">
            {/* Suspension is undone by an operator in seconds and tells nobody. Without this a
                tab that was locked out by mistake stays locked out until somebody thinks to
                reload it, with nothing on screen suggesting they should. */}
            {suspended && (
              <Button variant="primary" onPress={onRetry}>
                Try again
              </Button>
            )}
            <Button
              variant={suspended ? "outline" : "primary"}
              onPress={chooseAnother}
            >
              Choose another organization
            </Button>
            {/* Signing out drops the stored active Organization along with everything else
                this account left behind, or the next sign-in re-requests the very scope that
                locked this member out and lands them straight back on this screen. */}
            <Button
              variant="ghost"
              onPress={() => {
                signOut(auth);
              }}
            >
              Sign out
            </Button>
          </div>
        )}
      </div>
    </PreviewFrame>
  );
}
