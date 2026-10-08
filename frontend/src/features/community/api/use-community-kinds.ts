import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKindKeys } from "@/features/community/api/query-keys";

const gateway = httpCommunityGateway;

export function useCommunityKinds() {
  return useQuery({
    queryKey: communityKindKeys.list(),
    queryFn: () => gateway.listKinds(),
  });
}

export function useCreateKind() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (label: string) => gateway.createKind(label),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: communityKindKeys.all }),
  });
}

export function useUpdateKind() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, label }: { id: number; label: string }) =>
      gateway.renameKind(id, label),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: communityKindKeys.all }),
  });
}

interface DeleteKindInput {
  id: number;
  reassignToId?: number;
}

export function useDeleteKind() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reassignToId }: DeleteKindInput) =>
      gateway.deleteKind(id, reassignToId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: communityKindKeys.all }),
  });
}
