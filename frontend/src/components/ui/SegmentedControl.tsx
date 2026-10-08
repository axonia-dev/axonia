import { useId } from "react";
import { Label, ToggleButton, ToggleButtonGroup } from "react-aria-components";

import {
  segmentedGroupClass,
  segmentedItemClass,
} from "@/components/ui/segmented-styles";
import { cn } from "@/lib/cn";

interface SegmentedOption {
  id: string;
  label: string;
  /** Live item count rendered after the label; muted, accented on the active tab. */
  count?: number;
}

interface SegmentedControlProps {
  /** Accessible name for the group; rendered visibly unless `hideLabel`. */
  label: string;
  value: string;
  options: ReadonlyArray<SegmentedOption>;
  onChange: (value: string) => void;
  hideLabel?: boolean;
  className?: string;
}

const groupClass = segmentedGroupClass;

const buttonClass = cn(
  segmentedItemClass,
  "data-[selected]:bg-surface data-[selected]:text-foreground data-[selected]:shadow-sm",
);

/**
 * A pill-style single-choice segmented control over a small fixed set of
 * options. Single selection that can never be empty, so `value` is always one of
 * the option ids. Used where a toggle reads better than a dropdown (e.g. picking
 * the source of a new slot: backlog vs. a brand-new topic).
 */
export function SegmentedControl({
  label,
  value,
  options,
  onChange,
  hideLabel,
  className,
}: Readonly<SegmentedControlProps>) {
  const labelId = useId();
  return (
    // `min-w-0`: this wrapper, not the track, is the flex item when a caller sets a control
    // beside the group, so it is the one that has to be allowed to shrink for the track
    // inside it to wrap rather than push that control off a phone.
    <div className={cn("flex min-w-0 flex-col gap-1", className)}>
      <Label
        id={labelId}
        className={cn(
          "text-foreground text-sm font-medium",
          hideLabel && "sr-only",
        )}
      >
        {label}
      </Label>
      <ToggleButtonGroup
        selectionMode="single"
        disallowEmptySelection
        selectedKeys={[value]}
        onSelectionChange={(keys) => {
          const next = [...keys][0];
          if (next != null) {
            onChange(String(next));
          }
        }}
        aria-labelledby={labelId}
        className={groupClass}
      >
        {options.map((option) => (
          <ToggleButton key={option.id} id={option.id} className={buttonClass}>
            {option.label}
            {option.count !== undefined && (
              <span className="text-muted-foreground group-data-[selected]:text-primary ml-1.5 text-[10px] font-medium tabular-nums">
                {option.count}
              </span>
            )}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
    </div>
  );
}
