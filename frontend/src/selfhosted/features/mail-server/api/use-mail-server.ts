import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { ApiError } from "@/lib/api-client";
import type { Page } from "@/lib/page";
import { httpMailServerGateway } from "@/selfhosted/features/mail-server/adapters/http-mail-server-gateway";
import { mailServerKeys } from "@/selfhosted/features/mail-server/api/query-keys";
import type {
  MailServerChange,
  MailServerValues,
  SaveProof,
} from "@/selfhosted/features/mail-server/domain/mail-server";

const gateway = httpMailServerGateway;

type TestInput = Readonly<{ values: MailServerValues; recipient: string }>;
type SaveInput = Readonly<{ values: MailServerValues; proof: SaveProof }>;

export function useMailServer() {
  return useQuery({
    queryKey: mailServerKeys.view(),
    queryFn: () => gateway.read(),
  });
}

export function useMailConfigured() {
  return useQuery({
    queryKey: mailServerKeys.status(),
    queryFn: () => gateway.isConfigured(),
  });
}

function nextPage(last: Page<MailServerChange>): number | undefined {
  return last.number + 1 < last.totalPages ? last.number + 1 : undefined;
}

/** Newest first, read only once the history is opened. */
export function useMailServerChanges() {
  return useInfiniteQuery({
    queryKey: mailServerKeys.changes(),
    initialPageParam: 0,
    queryFn: ({ pageParam }) => gateway.changes(pageParam),
    getNextPageParam: nextPage,
  });
}

/** A typed password travels in a test's and a save's variables, which the mutation cache would
 *  otherwise keep for five minutes after the form closed. */
const FORGET_ON_UNMOUNT = 0;

/** Nothing is cached from a test: its token lives in the form that asked for it. */
export function useTestMailServer() {
  return useMutation({
    gcTime: FORGET_ON_UNMOUNT,
    mutationFn: ({ values, recipient }: TestInput) =>
      gateway.test(values, recipient),
  });
}

/** A save answers the new view, which replaces the cached one; whether mail is configured and the
 *  history follow from it. A save refused as stale reads the mail server again, so Cancel shows
 *  what changed; the open form keeps the revision it was opened at. */
function useRereadWhenStale() {
  const client = useQueryClient();
  return (error: Error) => {
    if (error instanceof ApiError && error.status === 409) {
      void client.invalidateQueries({ queryKey: mailServerKeys.view() });
    }
  };
}

export function useSaveMailServer() {
  const client = useQueryClient();
  const rereadWhenStale = useRereadWhenStale();
  return useMutation({
    gcTime: FORGET_ON_UNMOUNT,
    mutationFn: ({ values, proof }: SaveInput) => gateway.save(values, proof),
    onSuccess: (view) => {
      client.setQueryData(mailServerKeys.view(), view);
      void client.invalidateQueries({ queryKey: mailServerKeys.status() });
      void client.invalidateQueries({ queryKey: mailServerKeys.changes() });
    },
    onError: rereadWhenStale,
  });
}

/** Sent with the revision the form was opened at, and refused as stale like a save. */
export function useClearMailServer() {
  const client = useQueryClient();
  const rereadWhenStale = useRereadWhenStale();
  return useMutation({
    mutationFn: (baseRevision: number) => gateway.clear(baseRevision),
    onSuccess: () => client.invalidateQueries({ queryKey: mailServerKeys.all }),
    onError: rereadWhenStale,
  });
}
