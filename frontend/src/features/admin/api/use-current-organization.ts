import { useQuery } from "@tanstack/react-query";

import { useIsOrgless } from "@/auth/use-is-orgless";
import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpOrganizationsGateway;

/**
 * The Organization the caller's token is currently scoped to, incl. its `kind`
 * (ENTERPRISE vs HUB) — later tasks use `kind` to route the app shell. Disabled
 * until the token actually carries an `org` claim, so an org-less caller (e.g.
 * mid-switch) never fires a request doomed to fail RLS scoping.
 */
export function useCurrentOrganization() {
  return useQuery({
    queryKey: adminKeys.currentOrganization(),
    queryFn: () => gateway.getCurrentOrganization(),
    enabled: !useIsOrgless(),
  });
}
