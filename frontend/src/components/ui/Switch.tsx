import {
  SwitchButton,
  SwitchField,
  type SwitchFieldProps,
  Text,
} from "react-aria-components";

import { cn } from "@/lib/cn";

/**
 * The indent that lines a switch's description up under its label, and therefore the
 * indent anything else stacked beneath the control has to match. Exported rather than
 * restated at the call site: two copies of the number drift, and the only symptom is a
 * slightly ragged column nobody files a bug about.
 */
export const SWITCH_CONTROL_INDENT = "pl-12";

/**
 * Every switch needs an accessible name from exactly one source: either `label`, rendered here
 * and used as the name, or `aria-labelledby`, pointing at text a row already renders itself - see
 * `SessionMessagesCard`'s `SessionTimingRow`. The union makes the choice exclusive at the type
 * level rather than merely by convention: rendering `label` a second time when the caller also
 * supplies `aria-labelledby` is what made a screen reader announce a row's name twice in a row, and
 * omitting both would ship a switch with no accessible name at all.
 */
type SwitchLabelProps =
  | { label: string; "aria-labelledby"?: never }
  | { label?: never; "aria-labelledby": string };

export type SwitchProps = Omit<
  SwitchFieldProps,
  "className" | "children" | "aria-labelledby"
> &
  SwitchLabelProps & {
    description?: string;
    className?: string;
    /** Play the house confirmation pulse on the track. The caller owns the flag, because only
     *  the caller knows which change was the one the reader just made. */
    isPulsing?: boolean;
    /** Fired when that pulse finishes, so the caller can clear its own flag. */
    onPulseEnd?: () => void;
  };

export function Switch({
  label,
  description,
  className,
  isPulsing,
  onPulseEnd,
  ...props
}: Readonly<SwitchProps>) {
  return (
    <SwitchField {...props} className={cn("flex flex-col", className)}>
      <SwitchButton className="group flex cursor-pointer items-start gap-3 data-[disabled]:cursor-default">
        <span
          aria-hidden="true"
          data-slot="switch-track"
          onAnimationEnd={onPulseEnd}
          // Disabled fades rather than greys: the track keeps the hue that says on or off.
          // One neutral for both states makes a locked-on switch read as an off one.
          className={cn(
            "border-border bg-surface-sunken group-data-[selected]:bg-primary group-data-[focus-visible]:ring-focus-ring mt-0.5 flex h-5 w-9 shrink-0 rounded-full border p-0.5 transition-colors group-data-[disabled]:opacity-55 group-data-[focus-visible]:ring-2",
            isPulsing && "motion-safe:animate-[policy-pulse_500ms_ease-out]",
          )}
        >
          <span className="bg-muted-foreground group-data-[selected]:bg-primary-foreground h-3.5 w-3.5 rounded-full transition-transform group-data-[selected]:translate-x-4" />
        </span>
        {label !== undefined && (
          <span className="text-foreground text-sm font-medium">{label}</span>
        )}
      </SwitchButton>
      {description && (
        <Text
          slot="description"
          className={cn("text-muted-foreground text-sm", SWITCH_CONTROL_INDENT)}
        >
          {description}
        </Text>
      )}
    </SwitchField>
  );
}
