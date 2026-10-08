import { useQuery } from "@tanstack/react-query";

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";
import { artifactKeys } from "@/features/knowledge/api/query-keys";

const gateway = httpKnowledgeGateway;

/** Sessions that have at least one artifact, with their counts. */
export function useArtifactSessions(slug: string) {
  return useQuery({
    queryKey: artifactKeys.sessions(slug),
    queryFn: () => gateway.listSessions(slug),
  });
}

/** Vitality figures for the community's library header. */
export function useLibraryStats(slug: string) {
  return useQuery({
    queryKey: artifactKeys.stats(slug),
    queryFn: () => gateway.stats(slug),
  });
}
