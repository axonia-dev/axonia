import { Info } from "lucide-react";
import {
  Button as AriaButton,
  Dialog as AriaDialog,
  DialogTrigger,
  Popover,
} from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { pressableChipClass } from "@/components/ui/StatusChip";
import { useCalendarSyncStatus } from "@/features/events/api/use-calendar-subscription";
import {
  type CalendarSyncStatus,
  calendarSyncStatus,
} from "@/features/events/domain/calendar-sync-status";
import { useNow } from "@/lib/use-wake-at";

const popoverClass =
  "border-border-strong bg-surface w-[min(18rem,calc(100vw-1.5rem))] rounded-xl border p-3.5 shadow-lg outline-none";

function explanation({ state, provider }: CalendarSyncStatus): string {
  const subject = provider ?? "The calendar app";
  if (state === "waiting") {
    return "The calendar fetches this schedule on its own timing, usually within a few hours of adding the link.";
  }
  if (state === "stale") {
    return `${subject} has not fetched this schedule for over a day. Check that the calendar still holds the link, or reset it.`;
  }
  return `${subject} fetches this schedule on its own timing, usually every few hours. Changes made here appear there after its next fetch.`;
}

interface CalendarSyncChipProps {
  slug: string;
  onManage: () => void;
}

/** The member's calendar subscription at a glance; absent until there is one. */
export function CalendarSyncChip({
  slug,
  onManage,
}: Readonly<CalendarSyncChipProps>) {
  const { data } = useCalendarSyncStatus(slug);
  const now = useNow();
  if (data === undefined || data === null) return null;

  const status = calendarSyncStatus(data, now);
  const tone = status.state === "synced" ? "success" : "warning";
  return (
    <DialogTrigger>
      <AriaButton
        aria-label={
          status.provider === null
            ? status.label
            : `${status.label}, by ${status.provider}`
        }
        className={`${pressableChipClass(tone)} gap-1.5`}
      >
        <span
          aria-hidden
          className="size-1.5 flex-none rounded-full bg-current"
        />
        {status.label}
        {status.provider !== null && (
          <span className="hidden sm:inline">· {status.provider}</span>
        )}
        <Info size={12} aria-hidden className="opacity-70" />
      </AriaButton>
      <Popover placement="bottom end" className={popoverClass}>
        <AriaDialog aria-label="Calendar sync" className="outline-none">
          {({ close }) => (
            <div className="flex flex-col gap-3">
              <p className="text-foreground text-sm">{explanation(status)}</p>
              <div>
                <Button
                  variant="ghost"
                  size="sm"
                  className="-ml-3"
                  onPress={() => {
                    close();
                    onManage();
                  }}
                >
                  Calendar link settings
                </Button>
              </div>
            </div>
          )}
        </AriaDialog>
      </Popover>
    </DialogTrigger>
  );
}
