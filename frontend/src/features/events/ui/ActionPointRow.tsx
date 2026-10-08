import { Button, TooltipTrigger } from "react-aria-components";

import { Tooltip } from "@/components/ui/Tooltip";
import {
  nextOutcome,
  useSettleActionItem,
} from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { outcomeLabel } from "@/features/events/ui/action-item-labels";
import { ActionItemActions } from "@/features/events/ui/ActionItemActions";
import { ActionMeta } from "@/features/events/ui/ActionMeta";
import { OutcomeMark } from "@/features/events/ui/OutcomeMark";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

/** The row says "Not done" where the edit dialog says "Open": on a list of promises the absence
 *  of an answer is the point, and the button's name has to read as the state it currently holds.
 *  The two settled outcomes are named from the one shared spelling. */
function statusLabel(outcome: ActionItem["outcome"]): string {
  return outcome === "OPEN" ? "Not done" : outcomeLabel(outcome);
}

function nextMoveLabel(outcome: ActionItem["outcome"]): string {
  if (outcome === "OPEN") return "Mark done";
  if (outcome === "DONE") return "Mark won’t do";
  return "Reopen";
}

interface Props {
  slug: string;
  item: ActionItem;
  users: Record<string, User>;
  /** Suppresses the checkpoint chip where the surface IS the checkpoint. */
  hideCheckpoint?: boolean | undefined;
}

const controlClass =
  "data-[focus-visible]:ring-focus-ring data-[focus-visible]:ring-offset-surface mt-0.5 flex shrink-0 cursor-pointer rounded-[5px] outline-none data-[focus-visible]:ring-2 data-[focus-visible]:ring-offset-2";

interface ControlProps {
  item: ActionItem;
  settle: ReturnType<typeof useSettleActionItem>;
}

/**
 * The row's settle control: one button cycling open, done, dropped, gated by the item's own
 * `canClose`/`canReopen` rather than a role check here.
 *
 * Not a checkbox. Three mutually exclusive outcomes are not a checked/unchecked pair, and
 * `aria-checked="mixed"` claims something else entirely (partially checked). So it is a button
 * whose accessible name states the outcome it currently holds, and the tooltip names the move the
 * next press makes - without which a three-press cycle is undiscoverable.
 */
function StatusControl({ item, settle }: Readonly<ControlProps>) {
  if (!item.canClose && !item.canReopen) {
    return (
      <>
        <OutcomeMark outcome={item.outcome} className="mt-0.5" />
        <span className="sr-only">{statusLabel(item.outcome)}</span>
      </>
    );
  }
  return (
    <TooltipTrigger delay={400}>
      <Button
        aria-label={`${statusLabel(item.outcome)}: ${item.title}`}
        onPress={() => {
          settle.mutate(nextOutcome(item.outcome));
        }}
        className={controlClass}
      >
        <OutcomeMark outcome={item.outcome} className="mt-0.5" />
      </Button>
      <Tooltip placement="top">{nextMoveLabel(item.outcome)}</Tooltip>
    </TooltipTrigger>
  );
}

/**
 * One action item row: title, meta, and the control that settles it. A failed settle rolls the
 * cache back (see `useSettleActionItem`) and surfaces why, rather than leaving the button quietly
 * reverting.
 */
export function ActionPointRow({
  slug,
  item,
  users,
  hideCheckpoint,
}: Readonly<Props>) {
  const settle = useSettleActionItem(slug, item.id);
  const done = item.outcome === "DONE";
  const dropped = item.outcome === "DROPPED";

  return (
    <li className="border-border group relative flex gap-3 border-b py-3 last:border-b-0">
      <StatusControl item={item} settle={settle} />
      <div className="min-w-0 flex-1">
        <p
          className={cn(
            "text-sm leading-snug font-medium",
            done || dropped
              ? "text-muted-foreground line-through"
              : "text-foreground",
          )}
        >
          {item.title}
        </p>
        <ActionMeta
          item={item}
          users={users}
          slug={slug}
          hideCheckpoint={hideCheckpoint}
        />
      </div>
      <ActionItemActions slug={slug} item={item} />
    </li>
  );
}
