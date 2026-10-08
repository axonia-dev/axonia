import { type CalendarDate, parseDate } from "@internationalized/date";
import { Calendar as CalendarIcon } from "lucide-react";
import {
  Button as AriaButton,
  Calendar,
  CalendarCell,
  CalendarGrid,
  CalendarGridBody,
  CalendarGridHeader,
  CalendarHeaderCell,
  DateInput,
  DatePicker as AriaDatePicker,
  DateSegment,
  Dialog,
  Group,
  Heading,
  Label,
  Popover,
} from "react-aria-components";

import {
  dateFieldGroupClass,
  dateFieldLabelClass,
  dateSegmentClass,
} from "@/components/ui/date-field-styles";
import { cn } from "@/lib/cn";

interface Props {
  label: string;
  /** ISO `YYYY-MM-DD`, or `""` when empty. */
  value: string;
  onChange: (value: string) => void;
  isRequired?: boolean;
  /** ISO `YYYY-MM-DD`; a date before this is not offerable in the calendar popover. */
  minValue?: string;
  /** Marks the field as refused: the border turns, and assistive tech is told, so the reason
   *  stated beside it is not the only place the state exists. */
  isInvalid?: boolean;
  /** Keeps the label for assistive technology while a composing control shows its own. */
  hideLabel?: boolean;
  className?: string;
}

function toCalendarDate(value: string): CalendarDate | null {
  if (value.trim() === "") {
    return null;
  }
  try {
    return parseDate(value);
  } catch {
    return null;
  }
}

const navButtonClass =
  "flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground outline-none transition-colors hover:bg-surface-sunken data-[focus-visible]:ring-2 data-[focus-visible]:ring-focus-ring";

const cellClass =
  "flex h-8 w-8 cursor-pointer items-center justify-center rounded-md text-sm text-foreground outline-none transition-colors data-[outside-month]:text-muted-foreground data-[hovered]:bg-surface-sunken data-[selected]:bg-primary data-[selected]:text-primary-foreground data-[focus-visible]:ring-2 data-[focus-visible]:ring-focus-ring data-[disabled]:opacity-40";

// A month spans four to six weeks, and the grid renders one row per week, so the popover's height
// changed as the reader paged through months. Once a six-week month no longer fitted below the
// field, react-aria flipped the whole calendar above it, and it jumped back down on the next month.
// Holding the calendar at its tallest makes the placement decision the same for every month: the
// short months carry a blank row rather than moving the calendar out from under the pointer.
// 28px nav row, 8px gap, then the grid: a 16px weekday row, six 32px week rows, and 4px of
// border-spacing above and below each of the seven.
const calendarClass = "flex min-h-[276px] flex-col gap-2";

const popoverClass =
  "rounded-md border border-border-strong bg-surface p-3 shadow-lg outline-none data-[entering]:opacity-0 data-[exiting]:opacity-0 motion-reduce:transition-none";

/**
 * A labelled date field with a themed calendar popover, built on react-aria's
 * DatePicker. Replaces the native `<input type=date>` so the picker is keyboard-
 * and screen-reader accessible and the trigger icon follows the app theme
 * (the native calendar indicator ignored dark mode). Round-trips ISO strings.
 */
export function DatePicker({
  label,
  value,
  onChange,
  isRequired,
  isInvalid,
  hideLabel,
  className,
  minValue,
}: Readonly<Props>) {
  return (
    <AriaDatePicker
      value={toCalendarDate(value)}
      onChange={(date) => onChange(date ? date.toString() : "")}
      {...(isRequired !== undefined ? { isRequired } : {})}
      {...(isInvalid !== undefined ? { isInvalid } : {})}
      {...(minValue !== undefined
        ? { minValue: toCalendarDate(minValue) }
        : {})}
      className={cn("flex flex-col gap-1", className)}
    >
      <Label className={cn(dateFieldLabelClass, hideLabel && "sr-only")}>
        {label}
      </Label>
      <Group className={dateFieldGroupClass}>
        <DateInput className="flex flex-1 items-center">
          {(segment) => (
            <DateSegment segment={segment} className={dateSegmentClass} />
          )}
        </DateInput>
        <AriaButton
          className="text-muted-foreground data-[focus-visible]:ring-focus-ring flex items-center outline-none data-[focus-visible]:ring-2"
          aria-label="Open calendar"
        >
          <CalendarIcon aria-hidden className="h-4 w-4" />
        </AriaButton>
      </Group>
      <Popover className={popoverClass}>
        <Dialog className="outline-none">
          <Calendar className={calendarClass}>
            <header className="flex items-center justify-between gap-2 px-1">
              <AriaButton slot="previous" className={navButtonClass}>
                ‹
              </AriaButton>
              <Heading className="text-foreground text-sm font-semibold" />
              <AriaButton slot="next" className={navButtonClass}>
                ›
              </AriaButton>
            </header>
            <CalendarGrid className="border-separate border-spacing-1">
              <CalendarGridHeader>
                {(day) => (
                  <CalendarHeaderCell className="text-muted-foreground text-xs font-medium">
                    {day}
                  </CalendarHeaderCell>
                )}
              </CalendarGridHeader>
              <CalendarGridBody>
                {(date) => <CalendarCell date={date} className={cellClass} />}
              </CalendarGridBody>
            </CalendarGrid>
          </Calendar>
        </Dialog>
      </Popover>
    </AriaDatePicker>
  );
}
