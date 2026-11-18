import { useId } from "react";
import { Label, ToggleButton, ToggleButtonGroup } from "react-aria-components";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import { onBoard } from "@/features/mentoring/ui/logbook/plan";
import { trailOrder } from "@/features/mentoring/ui/trail/trail-layout";

/** The board's topics as toggles; with a cap, the rest wait once that many are ticked. */
export function TopicToggles({
  journal,
  selected,
  onChange,
  max,
}: Readonly<{
  journal: Journal;
  selected: number[];
  onChange: (topicIds: number[]) => void;
  max?: number;
}>) {
  const labelId = useId();
  if (journal.topics.length === 0) return null;
  const full = max !== undefined && onBoard(journal, selected).length >= max;
  return (
    <div className="flex flex-col gap-1.5">
      <Label id={labelId} className="text-foreground text-sm font-medium">
        Topics it names
      </Label>
      <ToggleButtonGroup
        aria-labelledby={labelId}
        selectionMode="multiple"
        selectedKeys={selected}
        onSelectionChange={(keys) => onChange([...keys].map(Number))}
        className="flex flex-wrap gap-1.5"
      >
        {trailOrder(journal.topics).map((topic) => (
          <ToggleButton
            key={topic.id}
            id={topic.id}
            isDisabled={full && !selected.includes(topic.id)}
            className="border-border bg-surface text-muted-foreground hover:border-border-strong focus-visible:ring-focus-ring data-[selected]:text-foreground inline-flex max-w-full cursor-pointer items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold outline-none focus-visible:ring-2 data-[disabled]:cursor-default data-[disabled]:opacity-50 data-[selected]:border-transparent data-[selected]:bg-[color-mix(in_srgb,var(--warning)_24%,var(--surface))]"
          >
            <span aria-hidden>✦</span>
            <span className="truncate">{topic.label}</span>
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
    </div>
  );
}
