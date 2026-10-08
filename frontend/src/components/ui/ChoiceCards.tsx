import type { ReactNode } from "react";
import {
  Label,
  RadioButton,
  RadioField,
  RadioGroup,
} from "react-aria-components";

import { cn } from "@/lib/cn";

export interface ChoiceCardOption {
  id: string;
  label: string;
  description: string;
  /** A mark for the choice, drawn ahead of its label. Decoration: it is hidden from assistive
   *  tech, so it may never carry anything the label and description do not already say. */
  icon?: ReactNode;
}

/**
 * Tier-1 radio cards: a small set of mutually exclusive choices where each option
 * deserves a sentence of explanation (unlike SegmentedControl's terse toggles).
 *
 * Built on RadioField + RadioButton (the plain `Radio` is deprecated upstream):
 * RadioField owns the card's border/selected styling (it exposes `isSelected`
 * but not focus), RadioButton fills the card as the actual clickable/focusable
 * label so the focus ring wraps the whole tile, not just its text.
 */
export function ChoiceCards({
  label,
  value,
  options,
  onChange,
}: Readonly<{
  label: string;
  value: string;
  options: ReadonlyArray<ChoiceCardOption>;
  onChange: (value: string) => void;
}>) {
  return (
    <RadioGroup
      value={value}
      onChange={onChange}
      className="flex flex-col gap-1.5"
    >
      <Label className="text-foreground text-sm font-medium">{label}</Label>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {options.map((option) => (
          <RadioField
            key={option.id}
            value={option.id}
            className={({ isSelected }) =>
              cn(
                "border-border rounded-md border transition-colors",
                isSelected
                  ? "border-primary bg-[color-mix(in_srgb,var(--primary)_8%,var(--surface))]"
                  : "hover:bg-surface-sunken",
              )
            }
          >
            <RadioButton
              className={({ isFocusVisible }) =>
                cn(
                  "block cursor-pointer rounded-md p-3 outline-none",
                  isFocusVisible && "ring-focus-ring ring-2",
                )
              }
            >
              {/* One flex row whether or not there is a mark: with none the gap collapses and
                  the card keeps the exact metrics it had before marks existed. */}
              <span className="flex items-start gap-2.5">
                {option.icon !== undefined && (
                  <span
                    aria-hidden
                    className="text-muted-foreground mt-0.5 flex shrink-0 items-center"
                  >
                    {option.icon}
                  </span>
                )}
                <span className="min-w-0">
                  <span className="text-foreground block text-sm font-medium">
                    {option.label}
                  </span>
                  <span className="text-muted-foreground block text-xs">
                    {option.description}
                  </span>
                </span>
              </span>
            </RadioButton>
          </RadioField>
        ))}
      </div>
    </RadioGroup>
  );
}
