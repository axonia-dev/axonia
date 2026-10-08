import { Button } from "@/components/ui/Button";
import type { ReactionTally } from "@/features/recognition/domain/reaction";
import { ReactionGlyph } from "@/features/recognition/ui/ReactionGlyph";
import { cn } from "@/lib/cn";

/** A single placement leaves the chip bare. A row where every reaction has drawn one person
 *  repeats the same digit a dozen times and says nothing by it; the number earns its place
 *  once it means more than one. */
function countLabel(count: number): string | null {
  return count > 1 ? String(count) : null;
}

/** A chip shows glyph and count only - the word lives in the tooltip and the
 * accessible label, never on the chip face.
 *
 * A standard emoji carries no `name`: the tally names a definition, and there is none
 * behind an emoji. The character itself stands in, which assistive tech reads out by
 * its CLDR name, so the chip is still named rather than announced as a bare count.
 *
 * The count joins the label rather than being left to the chip face: `aria-label` replaces
 * whatever a button contains, so a count drawn inside one is a count no screen reader ever
 * reaches. */
export function ReactionChip({
  tally,
  isPending,
  onToggle,
}: Readonly<{
  tally: ReactionTally;
  isPending: boolean;
  onToggle: () => void;
}>) {
  const name = tally.name ?? tally.emoji ?? "";
  const count = countLabel(tally.count);
  return (
    <span title={name === "" ? undefined : name}>
      <Button
        variant="ghost"
        size="sm"
        aria-label={count === null ? name : `${name}, ${count}`}
        aria-pressed={tally.viewerHasReacted}
        isDisabled={isPending}
        onPress={onToggle}
        className={cn(
          "gap-1 rounded-full border px-1.5 py-1 text-xs tabular-nums",
          // Placed-by-you keeps a trace of the primary rather than its full strength: love is
          // the pink on this row, and a strip of chips at the same weight drowns it (keeping
          // the two gestures legible is the surface's job).
          tally.viewerHasReacted
            ? "border-primary/45 bg-primary/8 text-foreground"
            : "border-border text-muted-foreground hover:border-border-strong",
        )}
      >
        <ReactionGlyph
          emoji={tally.emoji}
          imageUrl={tally.kind === "CUSTOM" ? tally.imageUrl : null}
          size="chip"
        />
        {count !== null && (
          <span aria-hidden className="pr-0.5 leading-none">
            {count}
          </span>
        )}
      </Button>
    </span>
  );
}
