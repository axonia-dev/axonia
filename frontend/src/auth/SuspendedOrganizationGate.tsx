import { useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";

import { OrganizationOffline } from "@/auth/OrganizationOffline";
import {
  type OrganizationOfflineReason,
  setOrganizationOfflineHandler,
} from "@/auth/token";

/**
 * Swaps the entire authenticated app for one honest screen the moment the backend refuses a
 * request because the Organization is no longer serving.
 *
 * It replaces rather than overlays: every query behind it is going to fail the same way, and a
 * shell that still looks usable while nothing in it works is worse than a screen that says so.
 * It sits above both branches of AuthenticatedFrame, because a delegated tab is just as
 * capable of standing in an Organization that has since been taken offline.
 *
 * Retrying clears the reason and throws away every cached failure with it. Without that the app
 * would come back holding the errors it collapsed on, and a member whose Organization was
 * unsuspended thirty seconds ago would meet the same wall they were just rescued from.
 */
export function SuspendedOrganizationGate({
  children,
}: Readonly<{ children: ReactNode }>) {
  const [reason, setReason] = useState<OrganizationOfflineReason | undefined>(
    undefined,
  );
  const queryClient = useQueryClient();

  useEffect(() => {
    setOrganizationOfflineHandler(setReason);
    return () => setOrganizationOfflineHandler(undefined);
  }, []);

  function retry() {
    setReason(undefined);
    void queryClient.resetQueries();
  }

  if (reason !== undefined)
    return <OrganizationOffline reason={reason} onRetry={retry} />;
  return <>{children}</>;
}
