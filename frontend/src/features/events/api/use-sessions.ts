import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  actionItemKeys,
  scheduleKeys,
  sessionKeys,
} from "@/features/events/api/query-keys";
import type {
  CreateOneOffInput,
  PageParams,
  UpdateSessionInput,
} from "@/features/events/domain/events-gateway";

const gateway = httpEventsGateway;

/**
 * The community's held sessions, for read-only surface composition (e.g. turning
 * an artifact's session id into a human label). A generous page covers a
 * community's history; callers fall back gracefully for anything beyond it.
 */
export function useSessions(slug: string) {
  const params = { page: 0, size: 100 };
  return useQuery({
    queryKey: sessionKeys.past(slug, params),
    queryFn: () => gateway.listPast(slug, params),
    select: (page) => page.content,
  });
}

export function useUpcomingSessions(slug: string, params: PageParams) {
  return useQuery({
    queryKey: sessionKeys.upcoming(slug, params),
    queryFn: () => gateway.listUpcoming(slug, params),
  });
}

/**
 * The most recently held session, or null if none was ever held.
 *
 * The status is the whole point of the read. A past page is every non-cancelled occurrence whose
 * hour has passed, which includes ones the community skipped and ones nobody ever confirmed either
 * way, so the newest row in it is not necessarily a session that happened. Every caller here speaks
 * about what the community did.
 */
export function useLastHeldSession(slug: string) {
  const params = { page: 0, size: 1, status: "HELD" } as const;
  return useQuery({
    queryKey: sessionKeys.past(slug, params),
    queryFn: () => gateway.listPast(slug, params),
    select: (page) => page.content[0] ?? null,
  });
}

/**
 * Count of sessions held since the given ISO instant (cheap size=1 read of totalElements).
 *
 * "Held" is asked for rather than assumed, for the same reason {@link useLastHeldSession} asks: an
 * unfiltered past window also counts the occurrences a community skipped and the ones nobody ever
 * confirmed, and a surface reading "sessions held this quarter" off that number states something
 * that did not happen.
 */
export function useSessionsHeldSince(slug: string, fromIso: string) {
  const params = { page: 0, size: 1, from: fromIso, status: "HELD" } as const;
  return useQuery({
    queryKey: sessionKeys.past(slug, params),
    queryFn: () => gateway.listPast(slug, params),
    select: (page) => page.totalElements,
  });
}

function invalidateScheduleAndSessions(
  queryClient: ReturnType<typeof useQueryClient>,
  slug: string,
  sessionId?: number,
) {
  const invalidations = [
    queryClient.invalidateQueries({ queryKey: scheduleKeys.all }),
    queryClient.invalidateQueries({ queryKey: sessionKeys.community(slug) }),
  ];
  // The single-session detail query (sessionKeys.detail) is not nested under
  // community(slug), so it must be invalidated explicitly — otherwise a detail
  // page (e.g. the SkippedBanner after a restore) keeps showing stale state.
  if (sessionId !== undefined) {
    invalidations.push(
      queryClient.invalidateQueries({
        queryKey: sessionKeys.detail(sessionId),
      }),
    );
  }
  return Promise.all(invalidations);
}

export function useEnsureOccurrence(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      seriesId,
      occurrenceDate,
    }: {
      seriesId: number;
      occurrenceDate: string;
    }) => gateway.ensureOccurrence(seriesId, occurrenceDate),
    onSuccess: (session) =>
      invalidateScheduleAndSessions(queryClient, slug, session.id),
  });
}

/**
 * The same, plus the community's action items.
 *
 * Skipping an occurrence, and restoring one, clears the checkpoints the server had pinned to it.
 * The change is invisible in the session payload, and nothing else refetches the items, so a
 * surface left mounted across it keeps rendering checkpoints that no longer exist.
 */
function invalidateScheduleSessionsAndActionItems(
  queryClient: ReturnType<typeof useQueryClient>,
  slug: string,
  sessionId: number,
) {
  return Promise.all([
    invalidateScheduleAndSessions(queryClient, slug, sessionId),
    queryClient.invalidateQueries({ queryKey: actionItemKeys.community(slug) }),
  ]);
}

export function useSkipOccurrence(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      seriesId,
      occurrenceDate,
    }: {
      seriesId: number;
      occurrenceDate: string;
    }) => gateway.skipOccurrence(seriesId, occurrenceDate),
    onSuccess: (session) =>
      invalidateScheduleSessionsAndActionItems(queryClient, slug, session.id),
  });
}

export function useUnskipOccurrence(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      seriesId,
      occurrenceDate,
    }: {
      seriesId: number;
      occurrenceDate: string;
    }) => gateway.unskipOccurrence(seriesId, occurrenceDate),
    onSuccess: (session) =>
      invalidateScheduleSessionsAndActionItems(queryClient, slug, session.id),
  });
}

export function useCreateOneOff(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateOneOffInput) => gateway.createOneOff(slug, input),
    onSuccess: () => invalidateScheduleAndSessions(queryClient, slug),
  });
}

export function useRescheduleSession(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      input,
    }: {
      sessionId: number;
      input: UpdateSessionInput;
    }) => gateway.updateSession(sessionId, input),
    onSuccess: (session) =>
      invalidateScheduleAndSessions(queryClient, slug, session.id),
  });
}
