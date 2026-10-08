import { Ban } from "lucide-react";

import { Button } from "@/components/ui/Button";
import type { MergeSuggestion } from "@/features/expertise/domain/expertise";

interface MergeTarget {
  variant: string;
  canonical: string;
}

interface Props {
  suggestion: MergeSuggestion;
  onMerge: (target: MergeTarget) => void;
  onDismiss: (pair: { tagA: string; tagB: string }) => void;
}

/**
 * A near-duplicate pair the curator should resolve: two tags that look like the
 * same thing. Each merge button shows the fold direction - the folded-away name,
 * an arrow, then the surviving name as a solid chip - so "merge into" is read
 * from the arrow rather than spelled out (the same chip-and-arrow language as the
 * batch merge bar). The quiet corner control parks the pair as "not a match" so a
 * genuinely-distinct pair stops resurfacing.
 */
export function MergeSuggestionCard({
  suggestion,
  onMerge,
  onDismiss,
}: Readonly<Props>) {
  const { tagA, tagB, score } = suggestion;
  const pct = Math.round(score * 100);
  // Both fold directions; the survivor is the tag the arrow points to.
  const directions = [
    { loser: tagB, survivor: tagA },
    { loser: tagA, survivor: tagB },
  ];
  return (
    <div className="relative rounded-xl border border-[color-mix(in_srgb,var(--primary)_22%,var(--border))] bg-[linear-gradient(180deg,color-mix(in_srgb,var(--primary)_7%,var(--surface)),var(--surface))] p-4">
      <Button
        variant="ghost"
        size="sm"
        aria-label="Not a match"
        className="text-muted-foreground hover:text-primary absolute top-2.5 right-2.5 p-1.5 hover:bg-transparent"
        onPress={() => onDismiss({ tagA, tagB })}
      >
        <Ban size={16} aria-hidden />
      </Button>
      <div className="flex flex-wrap items-center gap-2 pr-8">
        <span className="text-muted-foreground text-xs font-medium">
          Look-alike pair
        </span>
        <span className="text-muted-foreground border-border rounded-full border bg-[color-mix(in_srgb,var(--primary)_8%,transparent)] px-2 py-0.5 text-[11px]">
          {pct}%
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2.5">
        {directions.map(({ loser, survivor }) => (
          <Button
            key={survivor}
            variant="outline"
            size="sm"
            aria-label={`Merge ${loser} into ${survivor}`}
            onPress={() => onMerge({ variant: loser, canonical: survivor })}
          >
            <span className="text-muted-foreground font-medium">{loser}</span>
            <span aria-hidden className="text-muted-foreground">
              →
            </span>
            <span className="bg-primary text-primary-foreground rounded-full px-2 py-0.5 text-[11px] font-semibold">
              {survivor}
            </span>
          </Button>
        ))}
      </div>
    </div>
  );
}
