import type { Artifact } from "@/features/knowledge/domain/artifact";

/**
 * One row of the community home's Library feed: an artifact, attributed to whoever contributed it.
 * There is no ranking surface here, by design - see the recognition context's safeguard.
 */
export interface LibraryRow {
  key: string;
  title: string;
  contributorUserId: string | null;
  /** ISO instant the row is sorted by. */
  occurredAt: string;
  /** In-app navigation target: the Library focused on the item. */
  href: string;
  /** Content signal, never a member ranking. */
  loveCount: number;
  /** Artifact type id, which drives the resource icon and accent. */
  typeId: number;
  /** Artifact type label. */
  type: string;
}

/** The Library's newest first, capped. */
export function latestArtifacts(
  slug: string,
  artifacts: readonly Artifact[],
  limit = 6,
): LibraryRow[] {
  return artifacts
    .map((artifact) => ({
      key: `artifact-${artifact.id}`,
      title: artifact.title,
      contributorUserId: artifact.contributorUserId,
      occurredAt: artifact.createdAt,
      href: `/communities/${slug}/library?focus=${artifact.id}`,
      loveCount: artifact.loveCount,
      typeId: artifact.typeId,
      type: artifact.type,
    }))
    .sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt))
    .slice(0, limit);
}
