import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { useMe } from "@/features/identity/api/use-me";
import { writeMe } from "@/features/identity/api/write-me";
import { viewerZone } from "@/lib/zones";

const gateway = httpIdentityGateway;

/** The member's own answer, which wins over the observation without stopping it. */
export function useDeclareTimeZone() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (zone: string) => gateway.declareTimeZone(zone),
    onSuccess: writeMe(queryClient),
  });
}

/** Drops it, so the observation that kept refreshing underneath serves again. */
export function useClearTimeZone() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.clearTimeZone(),
    onSuccess: writeMe(queryClient),
  });
}

/**
 * Reports this browser's zone, and only when it has drifted from what is recorded. In the steady
 * state, which is every load after the first, it sends nothing.
 *
 * Mounted once, in `GlobalShell`, beside `useVisitRecorder` and for the same reason: `AppShell`
 * renders its header slots twice - the mobile one is hidden by CSS, not unmounted - so a write
 * hook mounted from a header component fires on every load rather than on drift.
 *
 * A failure is swallowed. A clock nobody could record is not worth a visible failure: the schedule
 * still renders, the spread note is one zone thinner, and the next load tries again.
 */
export function useReportObservedZone(): void {
  const me = useMe();
  const queryClient = useQueryClient();
  const recorded = me.data?.observedTimeZone ?? null;
  const loaded = me.data !== undefined;

  useEffect(() => {
    const zone = viewerZone();
    if (!loaded || recorded === zone) return;
    void gateway
      .reportObservedTimeZone(zone)
      .then(writeMe(queryClient))
      .catch(() => {});
  }, [loaded, recorded, queryClient]);
}
