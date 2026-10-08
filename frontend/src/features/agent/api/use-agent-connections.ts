import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpAgentConnectionGateway } from "../adapters/http-agent-connection-gateway";
import type { AuthorizationRequest } from "../domain/authorization-request";
import { agentKeys } from "./query-keys";

const gateway = httpAgentConnectionGateway;

export function useMyConnections() {
  return useQuery({
    queryKey: agentKeys.connections(),
    queryFn: () => gateway.listMine(),
  });
}

export function useDisconnectConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.disconnect(id),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: agentKeys.connections() });
    },
  });
}

/** A read with no side effect, so it is a query; an organization switch reloads the page and asks again. */
export function useAuthorizationReview(request: AuthorizationRequest) {
  return useQuery({
    queryKey: agentKeys.authorizationRequest(request),
    queryFn: () => gateway.review(request),
    retry: false,
    staleTime: Infinity,
  });
}

export function useApproveConnection() {
  return useMutation({
    mutationFn: ({
      request,
      scopes,
      organizationId,
    }: {
      request: AuthorizationRequest;
      scopes: string[];
      organizationId: string;
    }) => gateway.approve(request, scopes, organizationId),
  });
}

export function useDenyConnection() {
  return useMutation({
    mutationFn: (request: AuthorizationRequest) => gateway.deny(request),
  });
}
