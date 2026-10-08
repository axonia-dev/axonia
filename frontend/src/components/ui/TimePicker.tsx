import { parseTime, type Time } from "@internationalized/date";
import {
  DateInput,
  DateSegment,
  Label,
  TimeField as AriaTimeField,
} from "react-aria-components";

import {
  dateFieldGroupClass,
  dateFieldLabelClass,
  dateSegmentClass,
} from "@/components/ui/date-field-styles";
import { cn } from "@/lib/cn";

interface Props {
  label: string;
  /** ISO `HH:MM[:SS]`, or `""` when empty. Emits `HH:MM:SS`. */
  value: string;
  onChange: (value: string) => void;
  isRequired?: boolean;
  /** Marks the field as refused: the border turns, and assistive tech is told, so the reason
   *  stated beside it is not the only place the state exists. */
  isInvalid?: boolean;
  /** Keeps the label for assistive technology while a composing control shows its own. */
  hideLabel?: boolean;
  className?: string;
}

function toTime(value: string): Time | null {
  if (value.trim() === "") {
    return null;
  }
  try {
    return parseTime(value);
  } catch {
    return null;
  }
}

/**
 * A labelled time field built on react-aria's TimeField - the segmented,
 * accessible counterpart to the native `<input type=time>` (whose picker icon
 * ignored the app theme). Round-trips ISO time strings.
 */
export function TimePicker({
  label,
  value,
  onChange,
  isRequired,
  isInvalid,
  hideLabel,
  className,
}: Readonly<Props>) {
  return (
    <AriaTimeField
      value={toTime(value)}
      onChange={(time) => onChange(time ? time.toString() : "")}
      {...(isRequired !== undefined ? { isRequired } : {})}
      {...(isInvalid !== undefined ? { isInvalid } : {})}
      className={cn("flex flex-col gap-1", className)}
    >
      <Label className={cn(dateFieldLabelClass, hideLabel && "sr-only")}>
        {label}
      </Label>
      <DateInput className={dateFieldGroupClass}>
        {(segment) => (
          <DateSegment segment={segment} className={dateSegmentClass} />
        )}
      </DateInput>
    </AriaTimeField>
  );
}
