import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpRoundupGateway } from "@/features/admin/adapters/http-roundup-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";

const gateway = httpRoundupGateway;

/** The response is the freshly-read settings, so the cache is written straight from it
 *  rather than merely invalidated - the switch's own next render must reflect the new
 *  state without a second round trip. */
export function useUpdateRoundupEnabled() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => gateway.setEnabled(enabled),
    onSuccess: (settings) => {
      queryClient.setQueryData(adminKeys.roundupSettings(), settings);
    },
  });
}
