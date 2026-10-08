import { activeOrganizationId } from "@/auth/active-organization";
import { getAccessToken } from "@/auth/token";
import { useEdition } from "@/edition-context";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";

/** The id of the organization the session acts in: the token's `org` claim under token scope, or
 *  the installation's one membership, which no token names. `undefined` while it is not known. */
export function useActiveOrganizationId(): string | undefined {
  const { organizationScope } = useEdition();
  const installation = organizationScope === "installation";
  const { data: organizations } = useMyOrganizations({ enabled: installation });
  if (installation) return organizations?.[0]?.id;
  return activeOrganizationId(getAccessToken());
}
