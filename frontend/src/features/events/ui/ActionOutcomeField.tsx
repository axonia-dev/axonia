import {
  Label,
  RadioButton,
  RadioField,
  RadioGroup,
} from "react-aria-components";

import type { ActionItemOutcome } from "@/features/events/domain/action-item";
import { outcomeLabel } from "@/features/events/ui/action-item-labels";
import { OutcomeMark } from "@/features/events/ui/OutcomeMark";
import { cn } from "@/lib/cn";

const OUTCOMES: ReadonlyArray<ActionItemOutcome> = ["OPEN", "DONE", "DROPPED"];

const chipClass =
  "flex cursor-pointer items-center gap-2 rounded-full border py-1.5 pr-3.5 pl-2.5 text-[13px] font-semibold outline-none transition-colors";

/** The chosen chip takes its outcome's own colour, so a finished promise and an abandoned one
 *  never read alike. Open lifts onto the sunken surface, done warms to success, and one the
 *  community will not do stays deliberately colourless. */
function selectedClass(outcome: ActionItemOutcome): string {
  if (outcome === "DONE") {
    return "border-success/55 bg-success/15 text-success";
  }
  if (outcome === "DROPPED") {
    return "border-border-strong bg-surface-sunken text-muted-foreground";
  }
  return "border-border-strong bg-surface-sunken text-foreground";
}

interface Props {
  label: string;
  value: ActionItemOutcome;
  onChange: (outcome: ActionItemOutcome) => void;
}

/**
 * The status of an action item, picked from three chips that carry the very marks the row draws.
 * A radio group rather than a segmented tab strip: this changes the record, it does not switch
 * between views, and three outcomes are a choice rather than a checked/unchecked pair.
 */
export function ActionOutcomeField({
  label,
  value,
  onChange,
}: Readonly<Props>) {
  return (
    <RadioGroup
      value={value}
      onChange={(next) => {
        onChange(next as ActionItemOutcome);
      }}
      className="flex flex-col gap-1.5"
    >
      <Label className="text-foreground text-sm font-medium">{label}</Label>
      <div className="flex flex-wrap gap-2">
        {OUTCOMES.map((outcome) => (
          <RadioField key={outcome} value={outcome}>
            <RadioButton
              className={({ isSelected, isFocusVisible }) =>
                cn(
                  chipClass,
                  isSelected
                    ? selectedClass(outcome)
                    : "border-border text-muted-foreground hover:bg-surface-sunken",
                  isFocusVisible && "ring-focus-ring ring-2",
                )
              }
            >
              {({ isSelected }) => (
                <>
                  <OutcomeMark
                    outcome={outcome}
                    muted={!isSelected}
                    // `--muted` and `--surface-sunken` are the same colour in both themes, so
                    // the crossed box's own fill vanishes on a chosen chip. It takes the
                    // stronger border colour here to stay as solid as the tick beside it.
                    className={cn(
                      isSelected && outcome === "DROPPED" && "bg-border-strong",
                    )}
                  />
                  {/* The rule is drawn on the words alone: a strikethrough set on the chip
                      paints straight through the mark's box, which no jsdom assertion sees. */}
                  <span
                    className={cn(
                      isSelected && outcome === "DROPPED" && "line-through",
                    )}
                  >
                    {outcomeLabel(outcome)}
                  </span>
                </>
              )}
            </RadioButton>
          </RadioField>
        ))}
      </div>
    </RadioGroup>
  );
}
