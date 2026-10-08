import { useId } from "react";

import { SegmentedControl } from "@/components/ui/SegmentedControl";
import type {
  ActionItem,
  CheckpointOutcome,
} from "@/features/events/domain/action-item";
import { TruncationNotice } from "@/features/events/ui/TruncationNotice";

interface Props {
  items: readonly ActionItem[];
  /** How many the session owes in all, which is more than `items` once the page caps. */
  total: number;
  value: Record<number, CheckpointOutcome>;
  onChange: (value: Record<number, CheckpointOutcome>) => void;
}

const OUTCOME_OPTIONS = [
  { id: "DONE", label: "Done" },
  { id: "CARRIED", label: "Carry" },
  { id: "DROPPED", label: "Won’t do" },
] as const;

/**
 * One row per open item this session owes an answer on: title, assignee, and a
 * three-way choice defaulted to carry, the common outcome when the room runs out of
 * time. Never blocking - every row already carries a complete answer before anyone
 * touches it.
 *
 * Named and headed, because saving commits an answer for every row shown: an organizer here
 * only to paste a recording link, and a screen reader arriving at three bare toggles, both have
 * to be told what is being decided before the Save button does it.
 */
export function CheckpointOutcomeList({
  items,
  total,
  value,
  onChange,
}: Readonly<Props>) {
  const headingId = useId();

  if (items.length === 0) {
    return null;
  }

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-1">
      <h3 id={headingId} className="text-foreground text-sm font-medium">
        Open points
      </h3>
      <p className="text-muted-foreground text-xs">
        Saving records an outcome for every point listed.
      </p>
      <div className="border-border relative mt-1 flex max-h-64 flex-col gap-3 overflow-y-auto rounded-md border p-3">
        {items.map((item) => (
          <div
            key={item.id}
            className="flex items-center justify-between gap-3"
          >
            <div className="min-w-0">
              <p className="text-foreground truncate text-sm font-medium">
                {item.title}
              </p>
              <p className="text-muted-foreground truncate text-xs">
                {item.assigneeDisplayName ?? "Unassigned"}
              </p>
            </div>
            <SegmentedControl
              label={`Outcome for ${item.title}`}
              hideLabel
              value={value[item.id] ?? "CARRIED"}
              options={OUTCOME_OPTIONS}
              onChange={(next) =>
                onChange({ ...value, [item.id]: next as CheckpointOutcome })
              }
            />
          </div>
        ))}
      </div>
      <TruncationNotice
        shown={items.length}
        total={total}
        rest="The rest stay open on this session."
      />
    </section>
  );
}
