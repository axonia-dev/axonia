import { CalendarSync, ChevronDown, MoreHorizontal } from "lucide-react";
import {
  type Key,
  Menu as AriaMenu,
  MenuTrigger,
  Popover,
} from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { MenuItem, menuPopoverClass } from "@/components/ui/Menu";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { CalendarSyncChip } from "@/features/events/ui/CalendarSyncChip";

export const SAVED_VIEW = "saved";

const VIEW_OPTIONS = [
  { id: "timeline", label: "Timeline" },
  { id: "calendar", label: "Calendar" },
  { id: SAVED_VIEW, label: "Saved" },
] as const;

interface Props {
  slug: string;
  canStartSeries: boolean;
  /** The view shown: timeline, calendar or saved. */
  view: string;
  onViewChange: (next: string) => void;
  onNew: (key: Key) => void;
  onSubscribe: () => void;
}

export function ScheduleHeader({
  slug,
  canStartSeries,
  view,
  onViewChange,
  onNew,
  onSubscribe,
}: Readonly<Props>) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <h1 className="text-foreground font-display text-xl font-semibold">
        Schedule
      </h1>
      <div className="flex flex-wrap items-center gap-3">
        <CalendarSyncChip slug={slug} onManage={onSubscribe} />
        {canStartSeries && (
          <MenuTrigger>
            <Button variant="ghost" size="sm">
              + New
              <ChevronDown size={14} aria-hidden="true" />
            </Button>
            <Popover className={menuPopoverClass}>
              <AriaMenu onAction={onNew} className="outline-none">
                <MenuItem id="series">New series</MenuItem>
                <MenuItem id="oneoff">One-off session</MenuItem>
              </AriaMenu>
            </Popover>
          </MenuTrigger>
        )}
        <SegmentedControl
          label="Schedule view"
          hideLabel
          value={view}
          options={VIEW_OPTIONS}
          onChange={onViewChange}
        />
        {/* A neutral glyph over a labelled item, not a calendar icon: a calendar with a
            plus beside a calendar view reads as "add a session", which + New already is. */}
        <MenuTrigger>
          <IconButton label="Schedule actions">
            <MoreHorizontal size={15} />
          </IconButton>
          <Popover className={menuPopoverClass}>
            <AriaMenu onAction={onSubscribe} className="outline-none">
              <MenuItem id="subscribe">
                <CalendarSync size={14} />
                Add to calendar
              </MenuItem>
            </AriaMenu>
          </Popover>
        </MenuTrigger>
      </div>
    </div>
  );
}
