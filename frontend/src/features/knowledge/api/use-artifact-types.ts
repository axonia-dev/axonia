import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";
import { artifactTypeKeys } from "@/features/knowledge/api/query-keys";
import type { ArtifactTypeInput } from "@/features/knowledge/domain/knowledge-gateway";

const gateway = httpKnowledgeGateway;

export function useArtifactTypes() {
  return useQuery({
    queryKey: artifactTypeKeys.list(),
    queryFn: () => gateway.listTypes(),
  });
}

export function useCreateArtifactType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ArtifactTypeInput) => gateway.createType(input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: artifactTypeKeys.all }),
  });
}

export function useUpdateArtifactType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: ArtifactTypeInput }) =>
      gateway.updateType(id, input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: artifactTypeKeys.all }),
  });
}

export function useDeleteArtifactType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reassignToId }: { id: number; reassignToId?: number }) =>
      gateway.deleteType(id, reassignToId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: artifactTypeKeys.all }),
  });
}
