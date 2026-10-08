import { Plus } from "lucide-react";
import { useState } from "react";
import {
  Button as AriaButton,
  Dialog,
  DialogTrigger,
  Popover,
} from "react-aria-components";

import { iconButtonClass } from "@/components/ui/IconButton";
import {
  useReact,
  useReactions,
  useUnreact,
} from "@/features/recognition/api/use-reactions";
import {
  type ReactionRef,
  type ReactionTally,
  refKey,
  refOf,
} from "@/features/recognition/domain/reaction";
import { reactionStrip } from "@/features/recognition/domain/reaction-strip";
import type { ContributionKey } from "@/features/recognition/domain/recognition";
import { EmojiPicker } from "@/features/recognition/ui/EmojiPicker";
import { ProposeReactionDialog } from "@/features/recognition/ui/ProposeReactionDialog";
import { reactionPopoverClass } from "@/features/recognition/ui/reaction-popover";
import { ReactionChip } from "@/features/recognition/ui/ReactionChip";
import { ReactionOverflow } from "@/features/recognition/ui/ReactionOverflow";
import { cn } from "@/lib/cn";

/**
 * The chip strip: one chip per placed reaction up to what a row holds, the rest behind an
 * overflow control, then a quiet add control that opens {@link EmojiPicker} in a popover this
 * component owns. Sits before `LoveAction`, which keeps its own place, colour and shape
 * unchanged.
 *
 * The trigger is named "React" rather than "Add a reaction": the picker's own footer carries
 * that phrase, and it means the other thing - proposing a reaction the organization does not
 * have yet.
 */
export function ReactionBar({
  contribution,
  className,
}: Readonly<{ contribution: ContributionKey; className?: string }>) {
  const tallies = useReactions(contribution);
  const react = useReact(contribution);
  const unreact = useUnreact(contribution);
  const [isOpen, setIsOpen] = useState(false);
  const [isProposing, setIsProposing] = useState(false);

  function toggle(tally: ReactionTally) {
    const ref = refOf(tally);
    if (tally.viewerHasReacted) {
      unreact.mutate(ref);
    } else {
      react.mutate(ref);
    }
  }

  function pick(ref: ReactionRef) {
    react.mutate(ref);
    setIsOpen(false);
  }

  function propose() {
    // Closed first: the picker popover and the propose dialog are both overlays, and
    // leaving the popover open behind the dialog would stack the two.
    setIsOpen(false);
    setIsProposing(true);
  }

  const isTogglePending = react.isPending || unreact.isPending;
  const { visible, overflow } = reactionStrip(tallies.data ?? []);

  return (
    <div className={cn("flex flex-wrap items-center gap-1", className)}>
      {visible.map((tally) => (
        <ReactionChip
          key={refKey(refOf(tally))}
          tally={tally}
          isPending={isTogglePending}
          onToggle={() => toggle(tally)}
        />
      ))}
      {overflow.length > 0 && (
        <ReactionOverflow
          tallies={overflow}
          isPending={isTogglePending}
          onToggle={toggle}
        />
      )}
      <DialogTrigger isOpen={isOpen} onOpenChange={setIsOpen}>
        <AriaButton aria-label="React" className={iconButtonClass()}>
          <Plus aria-hidden size={14} />
        </AriaButton>
        <Popover placement="bottom start" className={reactionPopoverClass}>
          <Dialog aria-label="Reactions" className="outline-none">
            <EmojiPicker onPick={pick} onPropose={propose} />
          </Dialog>
        </Popover>
      </DialogTrigger>
      <ProposeReactionDialog
        isOpen={isProposing}
        onOpenChange={setIsProposing}
      />
    </div>
  );
}
