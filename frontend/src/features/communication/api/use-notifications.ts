import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";

import { httpNotificationGateway } from "../adapters/http-notification-gateway";
import type {
  NotificationCursor,
  NotificationPage,
} from "../domain/notification";
import { communicationKeys } from "./query-keys";

const gateway = httpNotificationGateway;

/** The cursor comes off the response, not off the last entry rendered: a window whose every row was dropped as an
 *  unrecognised type is empty with more genuinely behind it, and a derived cursor would have none to derive, ending
 *  the list there for good. A page claiming more with no cursor stops instead of re-asking with none, which would
 *  re-serve page one forever. */
function cursorFor(page: NotificationPage): NotificationCursor | undefined {
  if (!page.hasMore) return undefined;
  const { nextBefore, nextBeforeId } = page;
  if (nextBefore == null || nextBeforeId == null) return undefined;
  return { before: nextBefore, beforeId: nextBeforeId };
}

/** Newest first, a screenful at a time. Paged by cursor rather than by offset: the drain inserts rows at the head of
 *  this list while a member scrolls it, and an offset would skip or repeat them. */
export function useNotifications() {
  return useInfiniteQuery({
    queryKey: communicationKeys.notifications(),
    initialPageParam: undefined as NotificationCursor | undefined,
    queryFn: ({ pageParam }) => gateway.page(pageParam),
    getNextPageParam: cursorFor,
  });
}

function useInvalidateNotifications() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: communicationKeys.notifications(),
    });
    void queryClient.invalidateQueries({
      queryKey: communicationKeys.unreadCount(),
    });
  };
}

export function useMarkRead() {
  const invalidate = useInvalidateNotifications();
  return useMutation({
    mutationFn: (id: number) => gateway.markRead(id),
    onSettled: invalidate,
  });
}

export function useMarkAllRead() {
  const invalidate = useInvalidateNotifications();
  return useMutation({
    mutationFn: () => gateway.markAllRead(),
    onSettled: invalidate,
  });
}
