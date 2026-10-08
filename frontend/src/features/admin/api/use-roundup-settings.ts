import { useQuery } from "@tanstack/react-query";

import { httpRoundupGateway } from "@/features/admin/adapters/http-roundup-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpRoundupGateway;

/** `applies` is what keeps a Community Hub, or a caller without `communication:write`,
 *  from asking a question the backend answers with a refusal: the card gates the query
 *  rather than gating only what it renders. */
export function useRoundupSettings(applies: boolean) {
  return useQuery({
    queryKey: adminKeys.roundupSettings(),
    queryFn: () => gateway.getSettings(),
    enabled: applies,
  });
}

/** Never served as fresh: `staleTime: 0` means a reopened dialog refetches rather than
 *  settling for what it fetched last time. The cached copy is still shown while that
 *  request is in flight, for `gcTime`, so this is "always refetched", not "never held".
 *  The roundup covers whatever the closed quarter holds right now, and an administrator
 *  deciding on it has to be looking at today's rendering. */
export function useRoundupPreview(isOpen: boolean) {
  return useQuery({
    queryKey: adminKeys.roundupPreview(),
    queryFn: () => gateway.getPreview(),
    staleTime: 0,
    enabled: isOpen,
  });
}
