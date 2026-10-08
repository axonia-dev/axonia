import type { CurrentOrganization } from "@/features/admin/domain/current-organization";
import type { MyOrganization } from "@/features/admin/domain/my-organization";

// Outbound PORT for organization data. `listMyOrganizations` answers "which
// organizations may the signed-in caller enter" (token-scoped switching);
// `getCurrentOrganization` answers "which one is the token scoped to right now".
export interface OrganizationsGateway {
  listMyOrganizations(): Promise<MyOrganization[]>;
  getCurrentOrganization(): Promise<CurrentOrganization>;
  /** The organization-wide kill switch for outbound member messaging (the widest
   *  governance layer): `PUT .../current/communications-enabled`. */
  setCommunicationsEnabled(enabled: boolean): Promise<CurrentOrganization>;
}
