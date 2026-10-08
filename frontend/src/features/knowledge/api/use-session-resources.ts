import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";
import { artifactKeys } from "@/features/knowledge/api/query-keys";
import type {
  AttachResourceInput,
  DetachResourceInput,
} from "@/features/knowledge/domain/session-resource";

const gateway = httpKnowledgeGateway;

export function useSessionResources(slug: string, sessionId: number) {
  return useQuery({
    queryKey: artifactKeys.sessionResources(slug, sessionId),
    queryFn: () => gateway.sessionResources(slug, sessionId),
  });
}

export function useAttachResource(slug: string, sessionId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: AttachResourceInput) =>
      gateway.attachResource(slug, sessionId, input),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: artifactKeys.sessionResources(slug, sessionId),
      }),
  });
}

export function useDetachResource(slug: string, sessionId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: DetachResourceInput) =>
      gateway.detachResource(slug, sessionId, input),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: artifactKeys.community(slug),
      }),
  });
}
