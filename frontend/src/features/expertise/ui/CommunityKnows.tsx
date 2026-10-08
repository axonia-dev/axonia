import { EmptyState } from "@/components/patterns/EmptyState";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { TagConstellation } from "@/features/expertise/ui/TagConstellation";

interface Props {
  slug: string;
  onSelect: (tag: string) => void;
  activeTag?: string;
}

/**
 * The one list of areas this community knows, surfaced from /expertise/areas as
 * a persistent constellation so members see coverage without guessing a search
 * term. Lives inside the search panel; tapping a topic runs the search for it.
 */
export function CommunityKnows({ slug, onSelect, activeTag }: Readonly<Props>) {
  const { data, isLoading, isError } = useExpertiseAreas(slug);
  const areas = data ?? [];

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading topics…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load topics.
      </p>
    );
  }
  if (areas.length === 0) {
    return (
      <EmptyState
        title="Nothing shared yet"
        description="An area lands here once a member presents it, shares an artifact on it, or declares it."
      />
    );
  }

  return (
    <TagConstellation areas={areas} onSelect={onSelect} activeTag={activeTag} />
  );
}
