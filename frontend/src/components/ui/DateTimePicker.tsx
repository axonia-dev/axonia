import { Clock } from "lucide-react";
import { useId } from "react";

import { dateFieldLabelClass } from "@/components/ui/date-field-styles";
import { DatePicker } from "@/components/ui/DatePicker";
import { TimePicker } from "@/components/ui/TimePicker";
import { type ZonedParts } from "@/features/events/domain/zoned-instant";
import { cityOf } from "@/lib/zones";

interface Props {
  label: string;
  value: ZonedParts;
  onChange: (value: ZonedParts) => void;
  /** The zone the typed time is read in, stated beside the label. */
  timeZone: string;
  /** When false, an empty time is a supported answer rather than an omission. */
  isTimeRequired?: boolean;
  /** Marks both fields as refused, for a caller whose bound is on the pair rather than on either
   *  one. The reason itself belongs beside the control, where a reader can act on it.
   *
   *  Absent means "this caller has no opinion", which is not the same as `false`: forwarding a
   *  default `false` would pin react-aria's own validation state to valid and suppress the
   *  required-field invalidity the two fields work out for themselves. */
  isInvalid?: boolean;
  /** A fact the fields cannot show: the same moment on the viewer's clock, or what an empty
   *  time means. */
  description?: string;
}

/**
 * A date and a time, in a named zone. Replaces the native `datetime-local`, which rendered the
 * browser's own picker inside a themed dialog and, worse, said nothing about which zone it was
 * reading. The zone is a chip beside the label rather than a line beneath the fields, because it
 * qualifies the time and nothing else.
 *
 * The value is wall-clock parts rather than an instant: an instant cannot express an occurrence
 * with no hour, and composing one requires knowing which zone governs, which is the caller's
 * business and not this control's.
 *
 * The time field speaks `HH:MM:SS` and {@link ZonedParts} speaks `HH:MM`, so the seconds are
 * dropped here, at the boundary. A field whose value changes shape the moment it is touched is
 * how a caller's untouched-value comparison silently starts reporting a change.
 *
 * The two fields are one value, so they are wrapped in a named group carrying the description -
 * the same shape `MeetingPlaceFields` uses, and for the same reason. The description states a rule
 * about the pair ("leave the hour empty and it stays open", "what this comes to at home"), so hung
 * off neither field it reached nobody: a bare paragraph is not part of any accessible description,
 * and a screen reader announced the fields without the one sentence that qualifies them.
 */
export function DateTimePicker({
  label,
  value,
  onChange,
  timeZone,
  isTimeRequired = true,
  isInvalid,
  description,
}: Readonly<Props>) {
  const descriptionId = useId();
  return (
    <div
      role="group"
      aria-label={label}
      {...(description === undefined
        ? {}
        : { "aria-describedby": descriptionId })}
      className="flex flex-col gap-1"
    >
      <div className="flex items-center justify-between">
        <span className={dateFieldLabelClass}>{label}</span>
        <span className="text-muted-foreground bg-surface-sunken border-border inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs">
          <Clock aria-hidden className="h-3 w-3" />
          {cityOf(timeZone)}
        </span>
      </div>
      <div className="flex items-center gap-2.5">
        <DatePicker
          label={`${label} date`}
          hideLabel
          className="flex-1"
          value={value.date}
          onChange={(date) => onChange({ ...value, date })}
          isRequired
          {...(isInvalid === undefined ? {} : { isInvalid })}
        />
        <TimePicker
          label={`${label} time`}
          hideLabel
          className="w-28"
          value={value.time}
          onChange={(time) => onChange({ ...value, time: time.slice(0, 5) })}
          {...(isTimeRequired ? { isRequired: true } : {})}
          {...(isInvalid === undefined ? {} : { isInvalid })}
        />
      </div>
      {description !== undefined && (
        <p id={descriptionId} className="text-muted-foreground text-sm">
          {description}
        </p>
      )}
    </div>
  );
}
