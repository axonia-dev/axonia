import { Link } from "react-router-dom";

import { SectionCard } from "@/components/ui/SectionCard";
import { useTrendingTags } from "@/features/knowledge/api/use-library-insights";

/**
 * What the community is sharing about lately: the tags most carried by the
 * artifacts captured in the last 90 days, ranked by the server over the whole
 * library. Content signal, never a member ranking.
 */
export function TrendingTags({ slug }: Readonly<{ slug: string }>) {
  const tags = useTrendingTags(slug).data ?? [];

  if (tags.length === 0) {
    return null;
  }

  return (
    <SectionCard title="Trending tags" icon="hash" accent="var(--secondary)">
      <div className="flex flex-wrap gap-2 px-2 pt-1 pb-1">
        {tags.map(({ tag, count }) => (
          <Link
            key={tag}
            to={`/communities/${slug}/library?tag=${encodeURIComponent(tag)}`}
            className="bg-surface-sunken text-muted-foreground hover:bg-primary hover:text-primary-foreground inline-flex items-center gap-1 rounded-full px-3 py-1 text-sm transition-colors"
          >
            <span>#{tag}</span>
            <span className="text-xs opacity-60">{count}</span>
          </Link>
        ))}
      </div>
    </SectionCard>
  );
}
