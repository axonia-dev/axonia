import type { ActionItem } from "@/features/events/domain/action-item";
import { CarryChip, DueChip } from "@/features/events/ui/ActionItemChips";
import { AssigneeBadge } from "@/features/events/ui/AssigneeBadge";
import { CheckpointChip } from "@/features/events/ui/CheckpointChip";
import type { User } from "@/features/identity/domain/user";
import { useNow } from "@/lib/use-wake-at";

function assigneeUserOf(
  item: ActionItem,
  users: Record<string, User>,
): User | undefined {
  return users[item.assigneeUserId ?? ""];
}

interface ActionMetaProps {
  item: ActionItem;
  slug: string;
  users?: Record<string, User> | undefined;
  /** Suppresses the checkpoint chip where the surface IS the checkpoint. */
  hideCheckpoint?: boolean | undefined;
}

/** Assignee, due date, carry count and checkpoint for an action item row. The settle control lives
 *  with the row's own button, not here. Every chip is `ActionItemChips`', so the row and the Action
 *  items tab cannot word the same fact two ways. */
export function ActionMeta({
  item,
  slug,
  users = {},
  hideCheckpoint,
}: Readonly<ActionMetaProps>) {
  const now = useNow();

  return (
    <div className="mt-1.5 flex flex-wrap items-center gap-2">
      <AssigneeBadge
        name={item.assigneeDisplayName}
        user={assigneeUserOf(item, users)}
      />
      <DueChip dueDate={item.dueDate} now={now} />
      <CarryChip carryCount={item.carryCount} />
      {hideCheckpoint !== true && (
        <CheckpointChip checkpoint={item.checkpoint} slug={slug} />
      )}
    </div>
  );
}
