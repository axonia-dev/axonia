import { Button } from "@/components/ui/Button";
import type { MonthPosition } from "@/features/events/domain/schedule-position";
import {
  currentMonth,
  nextMonth,
  previousMonth,
  sameMonth,
} from "@/features/events/domain/schedule-position";
import { MonthYearPicker } from "@/features/events/ui/MonthYearPicker";
import { useNow } from "@/lib/use-wake-at";

const stepClass =
  "text-muted-foreground hover:text-foreground cursor-pointer rounded px-2 py-1 text-sm transition-colors";

export function MonthNav({
  month,
  activeMonths,
  onMonthChange,
}: Readonly<{
  month: MonthPosition;
  activeMonths: string[];
  onMonthChange: (month: MonthPosition) => void;
}>) {
  const now = currentMonth(useNow());
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-1">
        <button
          type="button"
          aria-label="Previous month"
          onClick={() => onMonthChange(previousMonth(month))}
          className={stepClass}
        >
          ‹
        </button>
        <MonthYearPicker
          year={month.year}
          monthIndex0={month.monthIndex0}
          activeMonths={activeMonths}
          onSelect={(year, monthIndex0) => onMonthChange({ year, monthIndex0 })}
        />
        <button
          type="button"
          aria-label="Next month"
          onClick={() => onMonthChange(nextMonth(month))}
          className={stepClass}
        >
          ›
        </button>
      </div>
      <Button
        variant="ghost"
        size="sm"
        isDisabled={sameMonth(month, now)}
        onPress={() => onMonthChange(now)}
      >
        Today
      </Button>
    </div>
  );
}
