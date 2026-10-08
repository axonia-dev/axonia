import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

import { httpPersonalAccessTokenGateway } from "../adapters/http-personal-access-token-gateway";
import type {
  CreateTokenBody,
  UpdateTokenBody,
} from "../domain/personal-access-token";
import { agentKeys } from "./query-keys";

const gateway = httpPersonalAccessTokenGateway;

export function useMyTokens() {
  return useQuery({
    queryKey: agentKeys.tokens(),
    queryFn: () => gateway.listMine(),
  });
}

export function useCreateToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTokenBody) => gateway.create(body),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: agentKeys.tokens() });
    },
  });
}

export function useRevokeToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.revoke(id),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: agentKeys.tokens() });
    },
  });
}

export function useUpdateToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateTokenBody }) =>
      gateway.update(id, body),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: agentKeys.tokens() });
    },
  });
}

/**
 * Whether this organization may connect agents at all.
 *
 * `isLoading` is separate from `available` on purpose: a cold mount must not read "not
 * answered yet" as "refused", which would eject an enterprise member from their own API
 * keys page before the query resolves. Same reasoning as `useOwnDataRights`.
 *
 * `resolved` says the backend actually answered. It is not the negation of `isLoading`: an
 * errored query is neither loading nor resolved. A notice explaining an absence must stay
 * silent then, because it would otherwise state a refusal nobody ever sent: a network blip
 * 500s here for an enterprise member too.
 *
 * `failure` is set while the read has failed with nothing to answer from, through a retry too.
 * The route gate shows it as a failed read rather than a refusal; the rail and the guide's links
 * stay closed, offering nothing that may not be there.
 */
export function useAgentAccess(): {
  available: boolean;
  isLoading: boolean;
  resolved: boolean;
  failure: RetryableRead | undefined;
} {
  const query = useQuery({
    queryKey: agentKeys.access(),
    queryFn: () => gateway.agentAccess(),
  });
  const failed = failedToLoad(query);
  return {
    available: query.data?.available ?? false,
    isLoading: query.data === undefined && !failed,
    resolved: query.data !== undefined,
    failure: failed ? query : undefined,
  };
}
