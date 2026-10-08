import {
  type QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";
import { artifactKeys } from "@/features/knowledge/api/query-keys";
import type {
  ArtifactSearchParams,
  CaptureArtifactInput,
  EditArtifactInput,
} from "@/features/knowledge/domain/knowledge-gateway";
import { recognitionKeys } from "@/features/recognition/api/query-keys";

const gateway = httpKnowledgeGateway;

export function useArtifacts(slug: string, params: ArtifactSearchParams) {
  return useQuery({
    queryKey: artifactKeys.search(slug, params),
    queryFn: () => gateway.search(slug, params),
  });
}

/** Every read that names an artifact's contributor: the library, expertise and My impact. */
function invalidateCreditReads(queryClient: QueryClient, slug: string) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: artifactKeys.community(slug) }),
    queryClient.invalidateQueries({ queryKey: expertiseKeys.all }),
    queryClient.invalidateQueries({
      queryKey: recognitionKeys.myContributions(),
    }),
  ]);
}

export function useCaptureArtifact(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CaptureArtifactInput) => gateway.capture(slug, input),
    onSuccess: () => invalidateCreditReads(queryClient, slug),
  });
}

export function useEditArtifact(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: EditArtifactInput }) =>
      gateway.edit(id, input),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: artifactKeys.community(slug),
      }),
  });
}

export function useChangeContributor(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, contributor }: { id: number; contributor: string }) =>
      gateway.changeContributor(id, contributor),
    onSuccess: () => invalidateCreditReads(queryClient, slug),
  });
}

export function useRemoveArtifact(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gateway.remove(id),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: artifactKeys.community(slug),
      }),
  });
}
