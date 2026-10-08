import {
  type QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { calendarSubscriptionKeys } from "@/features/events/api/query-keys";
import type { CalendarSubscription } from "@/features/events/domain/calendar-subscription";

const gateway = httpEventsGateway;

/**
 * How long a minted URL survives the dialog being closed. A mint or a reset writes its answer
 * into this cache, and that answer carries the one and only copy of the URL, so dropping the
 * entry on close would mean an Escape pressed by reflex destroys the link for good and sends
 * the member to a reset whose warning about calendars losing sync would not even be true.
 *
 * Bounded rather than the whole page's lifetime, because the fetch stamp travels in the same
 * entry: a tab left open overnight would otherwise still be reporting yesterday's sync, and
 * that line is the one thing a member opens this dialog to check.
 */
const KEEP_MINTED_URL_MS = 30 * 60 * 1000;

/**
 * Reads the member's subscription, and reads only: a subscription is a credential that
 * resolves publicly and that the API offers no way to delete, so nothing mints one as a side
 * effect of looking at this dialog. Null means the member has never subscribed, and the
 * dialog offers to.
 *
 * Never refetched while the dialog is open, for the same reason the entry is kept: a refetch
 * on window focus would blank the URL on screen at exactly the moment it is being pasted into
 * a calendar.
 */
export function useCalendarSubscription(slug: string) {
  return useQuery({
    queryKey: calendarSubscriptionKeys.community(slug),
    queryFn: () => gateway.getCalendarSubscription(slug),
    staleTime: Infinity,
    gcTime: KEEP_MINTED_URL_MS,
    refetchOnWindowFocus: false,
    retry: false,
  });
}

// Seeds the answer rather than invalidating: an invalidation refetches through the read,
// which answers without the URL that was just minted, throwing away its only copy.
function seed(
  queryClient: QueryClient,
  slug: string,
  subscription: CalendarSubscription,
) {
  queryClient.setQueryData(
    calendarSubscriptionKeys.community(slug),
    subscription,
  );
}

export function useSubscribeToCalendar(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.subscribeToCalendar(slug),
    onSuccess: (subscription) => seed(queryClient, slug, subscription),
  });
}

export function useResetCalendarSubscription(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.resetCalendarSubscription(slug),
    onSuccess: (subscription) => seed(queryClient, slug, subscription),
  });
}
