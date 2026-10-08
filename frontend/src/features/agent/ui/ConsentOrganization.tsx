import { useLocation } from "react-router-dom";

import { useEdition } from "@/edition-context";
import { useActiveOrganizationId } from "@/features/admin/api/use-active-organization-id";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import type { MyOrganization } from "@/features/admin/domain/my-organization";

function soleAndHeld(
  organizations: ReadonlyArray<MyOrganization>,
  activeId: string | undefined,
): boolean {
  return organizations.length === 1 && organizations[0]?.id === activeId;
}

/** The organization the connection is bound to. Where the edition supplies a picker, switching
 *  signs in again and comes back to this request; a token that holds no organization is always
 *  offered the choice, even of a sole one. */
export function ConsentOrganization() {
  const { OrganizationPicker } = useEdition();
  const { pathname, search } = useLocation();
  const { data: organizations } = useMyOrganizations();
  const activeId = useActiveOrganizationId();

  if (organizations === undefined) return null;
  if (organizations.length === 0) return <span>No organization</span>;
  if (OrganizationPicker === null || soleAndHeld(organizations, activeId)) {
    const held = organizations.find((org) => org.id === activeId);
    return <span>{held?.name ?? "No organization"}</span>;
  }
  return (
    <OrganizationPicker returnTo={`${pathname}${search}`} layout="select" />
  );
}
