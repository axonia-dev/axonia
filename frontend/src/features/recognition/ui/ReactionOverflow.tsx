import { Dialog, DialogTrigger, Popover } from "react-aria-components";

import { Button } from "@/components/ui/Button";
import {
  type ReactionTally,
  refKey,
  refOf,
} from "@/features/recognition/domain/reaction";
import { reactionPopoverClass } from "@/features/recognition/ui/reaction-popover";
import { ReactionChip } from "@/features/recognition/ui/ReactionChip";

/**
 * The reactions past the eighth, behind one control.
 *
 * They open into a popover rather than unfolding into the row: expanding in place moves every
 * control below it down the page, on a card the reader was already looking at. The chips
 * inside are the same chips, and toggle the same way, so nothing is read-only for being here.
 */
export function ReactionOverflow({
  tallies,
  isPending,
  onToggle,
}: Readonly<{
  tallies: ReactionTally[];
  isPending: boolean;
  onToggle: (tally: ReactionTally) => void;
}>) {
  const label = `${tallies.length} more reaction${tallies.length === 1 ? "" : "s"}`;
  return (
    <DialogTrigger>
      <Button
        variant="ghost"
        size="sm"
        aria-label={label}
        // h-7 rather than padding: the chips beside it are sized by their 18px glyph, and a
        // control whose height comes from a text line lands two pixels short of them.
        className="border-border text-muted-foreground hover:border-border-strong h-7 rounded-full border border-dashed px-2 text-xs tabular-nums"
      >
        +{tallies.length}
      </Button>
      <Popover placement="bottom" className={reactionPopoverClass}>
        <Dialog aria-label={label} className="outline-none">
          <div className="flex max-w-60 flex-wrap items-center gap-1 p-2">
            {tallies.map((tally) => (
              <ReactionChip
                key={refKey(refOf(tally))}
                tally={tally}
                isPending={isPending}
                onToggle={() => onToggle(tally)}
              />
            ))}
          </div>
        </Dialog>
      </Popover>
    </DialogTrigger>
  );
}
