import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";

import {
  autoEnterAttempted,
  markAutoEnterAttempted,
} from "@/auth/active-organization";
import { enterOrganization } from "@/auth/enter-organization";
import { useAuth } from "@/auth/session";
import { useIsOrgless } from "@/auth/use-is-orgless";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import type { MyOrganization } from "@/features/admin/domain/my-organization";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

export type ConsentOrganizationState =
  "held" | "entering" | "choose" | "none" | "failed";

/** The state, and the memberships read a "failed" state asks again through, so the failure
 *  screen never mounts an observer of its own that would ask again just by appearing. */
export type ConsentOrganization = Readonly<{
  state: ConsentOrganizationState;
  memberships: RetryableRead;
}>;

interface Memberships {
  organizations: ReadonlyArray<MyOrganization> | undefined;
  isError: boolean;
  attemptedBeforeMount: boolean;
}

function orglessStateOf({
  organizations,
  isError,
  attemptedBeforeMount,
}: Memberships): ConsentOrganizationState {
  if (organizations === undefined) return isError ? "failed" : "entering";
  if (organizations.length === 0) return "none";
  if (organizations.length === 1 && !attemptedBeforeMount) return "entering";
  return "choose";
}

/**
 * Whether the token holds the organization a connection binds to. A fresh sign-in holds none, and
 * this page sits outside DiscoverGate, so it enters a sole organization itself, under the same
 * one-shot guard, and comes back to this exact request.
 */
export function useConsentOrganization(): ConsentOrganization {
  const auth = useAuth();
  const { pathname, search } = useLocation();
  const orgless = useIsOrgless();
  const memberships = useMyOrganizations({ enabled: orgless });
  const organizations = memberships.data;
  const isError = failedToLoad(memberships);
  const [attemptedBeforeMount] = useState(autoEnterAttempted);
  const sole =
    orgless && organizations?.length === 1 ? organizations[0] : undefined;

  useEffect(() => {
    if (sole === undefined || autoEnterAttempted()) return;
    markAutoEnterAttempted();
    enterOrganization(auth, sole.id, `${pathname}${search}`);
  }, [auth, sole, pathname, search]);

  return {
    state: orgless
      ? orglessStateOf({ organizations, isError, attemptedBeforeMount })
      : "held",
    memberships,
  };
}
