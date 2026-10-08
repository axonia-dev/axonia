import { Navigate } from "react-router-dom";

import { ORGANIZATION_SETTINGS } from "@/components/patterns/settings-nav";
import { GroupAccessPage } from "@/features/admin/ui/GroupAccessPage";
import {
  useOrganizationDirectory,
  useResolvedBase,
} from "@/routes/settings-routes.hooks";

/**
 * Group access is served only where a directory exists to assert a group. The rail already drops
 * the row for a hub, but a URL outlives the rail: a bookmark, the `/admin/group-access` redirect,
 * or a hand-typed address would otherwise mount a page whose only endpoint 404s there.
 *
 * Registered by hand rather than through `ORGANIZATION_SECTIONS`, for the same reason vocabulary
 * and the account's own gated leaves are: a bare `ComponentType` in that map cannot carry a gate.
 */
export function OrganizationGroupAccessRoute({
  base,
}: Readonly<{ base: string }>) {
  const { hasDirectory, isLoading } = useOrganizationDirectory();
  const resolvedBase = useResolvedBase(base);
  // Belt and braces: `OrganizationSettingsShell` reads the same query and already renders
  // nothing until the kind resolves, so this branch is unreachable through the route tree as
  // registered. Kept because the cost of being wrong is asymmetric - without it, a cold mount
  // that ever did reach here would read "not answered yet" as "no directory" and eject an
  // enterprise administrator off their own page.
  if (isLoading) return null;
  if (!hasDirectory) {
    return <Navigate to={`${resolvedBase}/${ORGANIZATION_SETTINGS}`} replace />;
  }
  return <GroupAccessPage />;
}
