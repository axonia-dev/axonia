import { useEffect } from "react";

import {
  activeOrganizationId,
  clearStoredActiveOrganizationId,
  storedActiveOrganizationId,
} from "@/auth/active-organization";
import { useAuth } from "@/auth/session";

/**
 * Keeps the remembered Organization honest about what the session actually holds.
 *
 * The persisted `organization:<alias>` is folded into the login request so a member lands back
 * where they were. If that Organization has since vanished from the realm, Keycloak refuses the
 * whole authorize request with `invalid_scope`, and the edge recovers by dropping the organization
 * and signing the member in without it (edge/src/auth.ts). That recovery cannot reach localStorage,
 * so the dead alias would otherwise still be sitting here to be folded into the next login.
 *
 * This reconciles the two: a session whose token carries no `org` claim, while a stale one is still
 * remembered, forgets it. The token is the authority - it is what the backend reads - so anything
 * remembered that contradicts it is by definition out of date.
 *
 * Renders nothing; it is session plumbing mounted inside the provider.
 */
export function AuthScopeRecovery(): null {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isAuthenticated) return;
    if (activeOrganizationId(auth.user?.access_token) !== undefined) return;
    if (storedActiveOrganizationId() === undefined) return;

    clearStoredActiveOrganizationId();
  }, [auth.isAuthenticated, auth.user?.access_token]);

  return null;
}
