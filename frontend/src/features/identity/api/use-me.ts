import { useQuery } from "@tanstack/react-query";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { identityKeys } from "@/features/identity/api/query-keys";

/** The caller's own profile. Whatever an edition draws on the caller's avatar it resolves itself,
 *  from the subject. */
export function useMe() {
  return useQuery({
    queryKey: identityKeys.me,
    queryFn: () => httpIdentityGateway.me(),
  });
}
