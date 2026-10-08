import type { ContributionKey } from "@/features/recognition/domain/recognition";
import { LoveAction } from "@/features/recognition/ui/LoveAction";
import { ReactionBar } from "@/features/recognition/ui/ReactionBar";
import { cn } from "@/lib/cn";

/**
 * The divider only exists when love is drawn beside the chips. `LoveAction` renders nothing at
 * all when the viewer is the sole contributor and nobody has loved yet, and a rule keyed on
 * `:last-child` follows that on its own - there is no flag to keep in step and no way for a
 * hairline to be left stranded at the end of the row.
 */
const BEFORE_LOVE =
  "[&:not(:last-child)]:border-border-strong [&:not(:last-child)]:mr-1 [&:not(:last-child)]:border-r [&:not(:last-child)]:pr-2";

/**
 * Love in the row's right corner, the chips flowing in front of it.
 *
 * They are two gestures, not two halves of one: love credits the contributor and reaches
 * standing, expertise and My impact, while a reaction credits nobody at all. Any surface
 * showing both owes it to keep that legible, which is what the colour, the
 * shape and the divider between them are for - love is pink and unbordered, a reaction is an
 * outlined pill, and the hairline says where one ends.
 *
 * Love comes last and is the item that never shrinks: the strip is the part that grows a chip
 * at a time, so it is the part that has to wrap. Wrapping the two together instead put love on
 * a line of its own the moment a contribution drew more chips than the row held, taking the
 * hairline out of the row with it and leaving the two gestures reading as one list.
 */
export function ContributionActions({
  contribution,
  contributorUserIds,
}: Readonly<{
  contribution: ContributionKey;
  /** Current contributors; love mutes itself when the viewer is the sole one. */
  contributorUserIds?: string[];
}>) {
  return (
    <div className="flex items-center justify-end gap-1">
      <ReactionBar
        contribution={contribution}
        className={cn("min-w-0 justify-end", BEFORE_LOVE)}
      />
      <LoveAction
        contribution={contribution}
        {...(contributorUserIds ? { contributorUserIds } : {})}
      />
    </div>
  );
}
