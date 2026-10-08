import type { Artifact } from "@/features/knowledge/domain/artifact";

export interface TagCount {
  tag: string;
  count: number;
}

/**
 * Frequency-rank the tags across a window of recent artifacts. Content signal,
 * not a member ranking — surfacing what the community is sharing about.
 */
export function trendingTags(artifacts: Artifact[], limit = 6): TagCount[] {
  const counts = new Map<string, number>();
  for (const artifact of artifacts) {
    for (const tag of artifact.tags) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count || a.tag.localeCompare(b.tag))
    .slice(0, limit);
}
