import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";

import { httpNotificationGateway } from "../adapters/http-notification-gateway";
import { communicationKeys } from "./query-keys";

/** No polling interval and no persistent connection: the badge refetches when the window regains focus, which is when
 *  a member is in a position to act on it. A panel correct the moment it is opened is correct when it matters.
 *
 *  `refetchOnWindowFocus` is explicit here even though it stays `true`: the app-wide QueryClient default
 *  (`src/lib/query-client.ts`) turns it off, so this override documents that the badge deliberately opts back in.
 *
 *  The other half of the design is a refetch on navigation, keyed on the route's pathname only - a query-string edit
 *  is not a navigation a member expects to move the badge. TanStack Query has no built-in option for this, so it is
 *  driven here from `useLocation`, which means any consumer of this hook must be mounted inside a Router. */
export function useUnreadCount() {
  const queryClient = useQueryClient();
  const { pathname } = useLocation();
  const hasMounted = useRef(false);

  useEffect(() => {
    if (!hasMounted.current) {
      hasMounted.current = true;
      return;
    }
    // `cancelRefetch: false` because every shell mounts two bells - one per top bar, one visible
    // per breakpoint - so a navigation runs this effect twice in the same commit. The default
    // (`true`) makes the second call cancel the first fetch and issue another, and the cancelled
    // one is already on the wire since the gateway forwards no abort signal: a doubled request on
    // every navigation. Joining the fetch already in flight is exactly as fresh and costs one.
    void queryClient.invalidateQueries(
      { queryKey: communicationKeys.unreadCount() },
      { cancelRefetch: false },
    );
  }, [pathname, queryClient]);

  return useQuery({
    queryKey: communicationKeys.unreadCount(),
    queryFn: () => httpNotificationGateway.unreadCount(),
    refetchOnWindowFocus: true,
  });
}
