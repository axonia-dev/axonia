import { useState } from "react";
import {
  Button as AriaButton,
  Dialog,
  DialogTrigger,
  Popover,
} from "react-aria-components";

import { activityLookup } from "@/features/events/domain/schedule-activity";
import { cn } from "@/lib/cn";

interface Props {
  year: number;
  monthIndex0: number;
  activeMonths: string[];
  onSelect: (year: number, monthIndex0: number) => void;
}

const MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

function formatMonthYear(year: number, monthIndex0: number): string {
  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, monthIndex0, 1)));
}

const yearStepClass =
  "text-muted-foreground data-[hovered]:text-foreground cursor-pointer rounded px-2 py-1 outline-none transition-colors";

function monthButtonClass(selected: boolean): string {
  return cn(
    "relative cursor-pointer rounded px-2 py-1.5 text-sm outline-none",
    selected
      ? "bg-primary text-primary-foreground font-semibold"
      : "text-foreground data-[hovered]:bg-surface-sunken",
  );
}

export function MonthYearPicker({
  year,
  monthIndex0,
  activeMonths,
  onSelect,
}: Readonly<Props>) {
  const [isOpen, setIsOpen] = useState(false);
  const [viewedYear, setViewedYear] = useState(year);
  const hasActivity = activityLookup(activeMonths);

  function handleOpenChange(next: boolean) {
    if (next) setViewedYear(year); // reset to the calendar's year on each open
    setIsOpen(next);
  }

  function pick(mi: number) {
    onSelect(viewedYear, mi);
    setIsOpen(false);
  }

  return (
    <DialogTrigger isOpen={isOpen} onOpenChange={handleOpenChange}>
      <AriaButton
        aria-label={formatMonthYear(year, monthIndex0)}
        className="text-foreground font-display data-[focus-visible]:ring-focus-ring cursor-pointer rounded px-2 py-1 text-sm font-semibold outline-none data-[focus-visible]:ring-2"
      >
        {formatMonthYear(year, monthIndex0)}
      </AriaButton>
      <Popover className="border-border-strong bg-surface rounded-md border p-3 shadow-lg outline-none">
        <Dialog
          aria-label="Choose month and year"
          className="flex w-56 flex-col gap-3 outline-none"
        >
          <div className="flex items-center justify-between">
            <AriaButton
              aria-label="Previous year"
              onPress={() => setViewedYear((y) => y - 1)}
              className={yearStepClass}
            >
              ‹
            </AriaButton>
            <span className="text-foreground text-sm font-semibold">
              {viewedYear}
            </span>
            <AriaButton
              aria-label="Next year"
              onPress={() => setViewedYear((y) => y + 1)}
              className={yearStepClass}
            >
              ›
            </AriaButton>
          </div>
          <div className="grid grid-cols-3 gap-1">
            {MONTHS.map((label, mi) => {
              const selected = viewedYear === year && mi === monthIndex0;
              const hasDot = !selected && hasActivity(viewedYear, mi);
              return (
                <AriaButton
                  key={label}
                  aria-label={`${label} ${viewedYear}`}
                  onPress={() => pick(mi)}
                  className={monthButtonClass(selected)}
                >
                  {label}
                  {hasDot ? (
                    <span
                      aria-hidden
                      className="bg-primary absolute bottom-1 left-1/2 h-1.5 w-1.5 -translate-x-1/2 rounded-full"
                    />
                  ) : null}
                </AriaButton>
              );
            })}
          </div>
        </Dialog>
      </Popover>
    </DialogTrigger>
  );
}
