import { CalendarClock, RotateCcw } from "lucide-react";

import { isDueDateOverdue } from "@/features/events/domain/action-item";
import {
  carryLabel,
  dueDateLabel,
} from "@/features/events/ui/action-item-labels";
import { cn } from "@/lib/cn";

const CARRY_WARNING_THRESHOLD = 3;

const chipClass =
  "inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold";
const mutedChip = "bg-muted text-muted-foreground";
const dangerChip = "bg-danger/15 text-danger";
const warningChip = "border-warning/40 bg-warning/10 text-warning";
const passedDueChip = "bg-warning/10 text-warning";

/**
 * When the work is meant to land. Always "Due <date>", whether or not that date has passed: the
 * date is a target, and the checkpoint is what says whether the item is late. A passed date is
 * tinted but borderless, so `CheckpointStatusChip`'s bordered warning still reads louder - an
 * unreported checkpoint is the bigger problem.
 */
export function DueChip({
  dueDate,
  now,
}: Readonly<{ dueDate: string | null; now: Date }>) {
  if (dueDate === null) return null;
  const passed = isDueDateOverdue(dueDate, now);
  return (
    <span className={cn(chipClass, passed ? passedDueChip : mutedChip)}>
      <CalendarClock className="h-3 w-3" />
      Due {dueDateLabel(dueDate)}
    </span>
  );
}

/**
 * An overdue due date and an unreported checkpoint are different problems: the first is
 * about the work itself, the second about a session that already happened without the
 * item being checked off. They get separate chips so neither hides the other.
 */
export function CheckpointStatusChip({
  unresolved,
  unreported,
}: Readonly<{ unresolved: boolean; unreported: boolean }>) {
  if (unresolved) {
    return <span className={cn(chipClass, mutedChip)}>Checkpoint pending</span>;
  }
  if (unreported) {
    return (
      <span className={cn(chipClass, warningChip)}>
        <RotateCcw className="h-3 w-3" />
        Checkpoint not reported
      </span>
    );
  }
  return null;
}

export function CarryChip({ carryCount }: Readonly<{ carryCount: number }>) {
  const label = carryLabel(carryCount);
  if (label === null) return null;
  const isProblem = carryCount >= CARRY_WARNING_THRESHOLD;
  return (
    <span className={cn(chipClass, isProblem ? dangerChip : mutedChip)}>
      {label}
    </span>
  );
}
