import type { CSSProperties } from "react";

import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { resolveLabel } from "@/features/events/domain/schedule";
import type { Series } from "@/features/events/domain/series";
import { entryKey } from "@/features/events/ui/DayCell";
import { longDayLabel } from "@/features/events/ui/schedule-dates";
import { Chip, chipHref } from "@/features/events/ui/ScheduleChip";
import { SessionPanel } from "@/features/events/ui/SessionPanel";
import type { User } from "@/features/identity/domain/user";

/** Composes SessionPanel from the pieces both the desktop and phone call sites hold, so neither
 *  repeats the entry-to-panel-props mapping. */
export function SelectedSessionPanel({
  selected,
  seriesList,
  slug,
  users,
  panelId,
  focusOnOpen,
  onClose,
  className,
  style,
}: Readonly<{
  selected: ScheduleEntry;
  seriesList: Series[];
  slug: string;
  users: Record<string, User>;
  panelId: string;
  focusOnOpen: boolean;
  onClose: () => void;
  className?: string;
  /** Geometry a class cannot express: the desktop panel's own left edge, computed from the width
   *  the page gives the calendar. */
  style?: CSSProperties;
}>) {
  return (
    <SessionPanel
      entry={selected}
      id={panelId}
      label={resolveLabel(selected, seriesList)}
      href={chipHref(selected, slug)}
      users={users}
      focusOnOpen={focusOnOpen}
      onClose={onClose}
      {...(className !== undefined && { className })}
      {...(style !== undefined && { style })}
    />
  );
}

interface DayDetailProps {
  selectedDate: string;
  dayEntries: ScheduleEntry[];
  seriesList: Series[];
  slug: string;
  onOpen: (entry: ScheduleEntry) => void;
  onSelect: (entry: ScheduleEntry) => void;
  /** The session panel state, grouped so it reads as one concern rather than four loose
   *  parameters. */
  panel: {
    selected: ScheduleEntry | null;
    users: Record<string, User>;
    panelId: string;
    focusOnOpen: boolean;
    onClose: () => void;
  };
}

/** Mobile-only panel listing the selected day's sessions, with the session panel for whichever
 *  one was tapped rendered underneath. */
export function DayDetail({
  selectedDate,
  dayEntries,
  seriesList,
  slug,
  onOpen,
  onSelect,
  panel,
}: Readonly<DayDetailProps>) {
  return (
    <div className="md:hidden" data-testid="day-detail">
      <h3 className="text-foreground mb-2 text-sm font-semibold">
        {longDayLabel(selectedDate)}
      </h3>
      {dayEntries.length === 0 ? (
        <p className="text-muted-foreground text-sm">No sessions.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {dayEntries.map((entry, idx) => (
            <Chip
              key={entryKey(entry, selectedDate, idx)}
              entry={entry}
              seriesList={seriesList}
              selected={entry === panel.selected}
              panelId={panel.panelId}
              onSelect={() => onSelect(entry)}
              onOpen={() => onOpen(entry)}
            />
          ))}
        </div>
      )}
      {panel.selected !== null && (
        <SelectedSessionPanel
          selected={panel.selected}
          seriesList={seriesList}
          slug={slug}
          users={panel.users}
          panelId={panel.panelId}
          focusOnOpen={panel.focusOnOpen}
          onClose={panel.onClose}
          className="mt-3"
        />
      )}
    </div>
  );
}
