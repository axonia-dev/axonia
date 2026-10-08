import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpScimTokenGateway } from "@/features/admin/adapters/http-scim-token-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";
import type { GenerateTokenBody } from "@/features/admin/domain/scim-token";

const gateway = httpScimTokenGateway;

export function useScimTokens() {
  return useQuery({
    queryKey: adminKeys.scimTokens(),
    queryFn: () => gateway.listTokens(),
  });
}

export function useGenerateScimToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: GenerateTokenBody) => gateway.generateToken(body),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.scimTokens() });
    },
  });
}

export function useRevokeScimToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tokenId: number) => gateway.revokeToken(tokenId),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.scimTokens() });
    },
  });
}
