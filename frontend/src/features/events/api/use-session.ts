import {
  skipToken,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  actionItemKeys,
  scheduleKeys,
  sessionKeys,
} from "@/features/events/api/query-keys";
import type {
  CloseSessionInput,
  FollowUpInput,
  ReleaseMessageType,
  UpcomingReleaseInput,
  WrapUpReleaseInput,
} from "@/features/events/domain/events-gateway";

const gateway = httpEventsGateway;

/**
 * A single session by id, with its full section → slot tree.
 *
 * A null id is a projected ghost: an occurrence the server has no row for yet, so there is nothing
 * to read. The calendar holds entries of both kinds and cannot call this conditionally, which is
 * what the null arm is for.
 *
 * That arm is `skipToken` rather than `enabled: false`, so the absence is carried by the type: the
 * only branch that names a request is the one that has an id to put in it, and there is no path -
 * a `refetch()` included - that can reach the endpoint with nothing to ask for.
 */
export function useSession(sessionId: number | null) {
  return useQuery({
    queryKey: sessionKeys.detail(sessionId),
    queryFn:
      sessionId === null ? skipToken : () => gateway.getSession(sessionId),
  });
}

function invalidateSessionAndSchedule(
  queryClient: ReturnType<typeof useQueryClient>,
  slug: string,
  sessionId: number,
) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: scheduleKeys.all }),
    queryClient.invalidateQueries({ queryKey: sessionKeys.community(slug) }),
    queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) }),
  ]);
}

/**
 * The same, plus the community's action items.
 *
 * Settling a session's fate rewrites which session an item is checked at: closing one pins every
 * late-bound open item of the community to it, cancelling or skipping one clears the pins it held.
 * None of that is visible in the session's own payload, and nothing else refetches the items -
 * `staleTime` is 30s and `refetchOnWindowFocus` is off - so a surface left mounted across the
 * change keeps rendering the page it read before it.
 */
function invalidateSessionScheduleAndActionItems(
  queryClient: ReturnType<typeof useQueryClient>,
  slug: string,
  sessionId: number,
) {
  return Promise.all([
    invalidateSessionAndSchedule(queryClient, slug, sessionId),
    queryClient.invalidateQueries({ queryKey: actionItemKeys.community(slug) }),
  ]);
}

export function useCancelSession(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: number) => gateway.cancelSession(sessionId),
    onSuccess: (_data, sessionId) =>
      invalidateSessionScheduleAndActionItems(queryClient, slug, sessionId),
  });
}

/** The organizer's answer to "did this happen?" - held stamps the wrap-up release,
 *  not-held cancels the session (releasing its slotted topics back to the backlog). */
export function useCloseSession(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      input,
    }: {
      sessionId: number;
      input: CloseSessionInput;
    }) => gateway.closeSession(sessionId, input),
    onSuccess: (_data, { sessionId }) =>
      invalidateSessionScheduleAndActionItems(queryClient, slug, sessionId),
  });
}

/**
 * The recording and the organizer's note, captured after the fact.
 *
 * Both fields feed the wrap-up mail as well as the community overview's marquee, which is why this
 * exists at all: the endpoint shipped long before anything in the app called it, so every session
 * in the product carried a null recording and a null note whatever actually happened in the room.
 *
 * The same pass settles the session's open checkpoints, so the action-item cache is stale the
 * moment it lands. Nothing else refetches it - `staleTime` is 30s and `refetchOnWindowFocus` is
 * off - and a dialog reopened inside that window would list the items it has already carried and
 * answer for them again, which the server refuses as NOT_PINNED_HERE and rolls the whole
 * follow-up back with.
 */
export function useRecordFollowUp(slug: string, sessionId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: FollowUpInput) =>
      gateway.recordFollowUp(sessionId, input),
    onSuccess: () =>
      invalidateSessionScheduleAndActionItems(queryClient, slug, sessionId),
  });
}

/** Moves, or permanently suppresses (null), a held session's wrap-up release instant. */
export function useSetWrapUpRelease(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      input,
    }: {
      sessionId: number;
      input: WrapUpReleaseInput;
    }) => gateway.setWrapUpRelease(sessionId, input),
    onSuccess: (_data, { sessionId }) =>
      invalidateSessionAndSchedule(queryClient, slug, sessionId),
  });
}

/** Sends, schedules or withholds a scheduled session's reminder. Which of the three the
 *  payload says is `UpcomingReleaseInput`'s business, not this hook's. */
export function useSetUpcomingRelease(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      input,
    }: {
      sessionId: number;
      input: UpcomingReleaseInput;
    }) => gateway.setUpcomingRelease(sessionId, input),
    onSuccess: (_data, { sessionId }) =>
      invalidateSessionAndSchedule(queryClient, slug, sessionId),
  });
}

/**
 * The subject and HTML a send of `messageType` would produce for this session right now.
 * `enabled` lets `ReleasePreviewDialog` hold off the request until it is actually open -
 * React Aria's `Modal` unmounts a closed dialog's contents, but this hook still has to be
 * called on every render, so gating the query is how a mounted-but-closed dialog stays a
 * request never sent. `staleTime: 0` makes reopening the dialog always fetch fresh rather
 * than reuse a preview rendered before the organizer's last edit.
 */
export function useMessagePreview(
  slug: string,
  sessionId: number,
  messageType: ReleaseMessageType,
  enabled: boolean,
) {
  return useQuery({
    queryKey: sessionKeys.messagePreview(sessionId, messageType),
    queryFn: () => gateway.getMessagePreview(slug, sessionId, messageType),
    staleTime: 0,
    enabled,
  });
}
