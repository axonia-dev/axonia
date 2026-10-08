import { useQuery } from "@tanstack/react-query";

import { httpPrivacyGateway } from "@/features/privacy/adapters/http-privacy-gateway";
import { privacyKeys } from "@/features/privacy/api/query-keys";

const gateway = httpPrivacyGateway;

/**
 * Whether the caller may delete their own account right now. Mounted only while the
 * delete dialog is open (React Aria unmounts a closed Modal's contents), so a fresh
 * mount is the moment this must refetch - `staleTime: 0` overrides the shared
 * `queryClient`'s 30s default, which would otherwise serve a cached answer to
 * someone who reopens the dialog shortly after changing which communities they own.
 *
 * `enabled` lets the dialog hold off the request entirely when it has nothing to
 * confirm against yet (the account's email is still unknown) - the hook still has
 * to be called unconditionally, so gating the query is how that early return stays
 * a query the dialog never sends rather than one it sends and discards.
 */
export function useErasurePreflight(enabled: boolean) {
  return useQuery({
    queryKey: privacyKeys.erasurePreflight(),
    queryFn: () => gateway.erasurePreflight(),
    staleTime: 0,
    enabled,
  });
}
