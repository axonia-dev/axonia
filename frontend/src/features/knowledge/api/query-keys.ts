import type { ArtifactSearchParams } from "@/features/knowledge/domain/knowledge-gateway";

export const artifactKeys = {
  all: ["knowledge", "artifacts"] as const,
  community: (slug: string) => [...artifactKeys.all, slug] as const,
  search: (slug: string, params: ArtifactSearchParams) =>
    [...artifactKeys.community(slug), params] as const,
  sessionResources: (slug: string, sessionId: number) =>
    [...artifactKeys.community(slug), "session-resources", sessionId] as const,
  sessions: (slug: string) =>
    [...artifactKeys.community(slug), "sessions"] as const,
  stats: (slug: string) => [...artifactKeys.community(slug), "stats"] as const,
};

export const artifactTypeKeys = {
  all: ["knowledge", "artifact-types"] as const,
  list: () => [...artifactTypeKeys.all, "list"] as const,
};
