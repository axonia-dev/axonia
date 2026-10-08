import {
  organizationScope,
  setStoredActiveOrganizationId,
} from "@/auth/active-organization";

/** The slice of the session entering needs: the authorize round trip, and nothing else. */
export interface Reauthorizer {
  signinRedirect: (args?: {
    scope?: string;
    state?: { returnTo?: string };
  }) => Promise<void>;
}

/**
 * Enters an organization the caller belongs to: persists it as the active one, then
 * re-authorizes scoped to it, since a silent refresh cannot add an organization scope.
 * The redirect reloads the app.
 *
 * `returnTo` is stated rather than left to default, because a login with no `returnTo` comes
 * back to the path it left from, which is nearly always the page holding the button that
 * asked. The root lands by kind. A caller with an address of its own to come back to, the
 * consent page above all, passes it.
 */
export function enterOrganization(
  auth: Reauthorizer,
  id: string,
  returnTo = "/",
): void {
  setStoredActiveOrganizationId(id);
  void auth.signinRedirect({
    scope: organizationScope(id),
    state: { returnTo },
  });
}
