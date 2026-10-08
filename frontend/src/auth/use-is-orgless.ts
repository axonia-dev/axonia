import { activeOrganizationId } from "@/auth/active-organization";
import { getAccessToken } from "@/auth/token";
import { useEdition } from "@/edition-context";

/** A signed-in person whose session holds no organization. Under token scope that is a token with
 *  no `org` claim, read on every render so a scope switch is seen as soon as the new token is
 *  registered; an installation's session is always in its one organization. */
export function useIsOrgless(): boolean {
  const { organizationScope } = useEdition();
  if (organizationScope === "installation") return false;
  return activeOrganizationId(getAccessToken()) === undefined;
}
