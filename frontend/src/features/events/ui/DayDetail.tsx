import type { CSSProperties } from "react";

import { sessionMorphId } from "@/components/patterns/morph/MorphPart";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { entryKey } from "@/features/events/ui/DayCell";
import { longDayLabel } from "@/features/events/ui/schedule-dates";
import { Chip, chipHref } from "@/features/events/ui/ScheduleChip";
import { SessionPanel } from "@/features/events/ui/SessionPanel";
import type { User } from "@/features/identity/domain/user";

/** Composes SessionPanel from the pieces both the desktop and phone call sites hold, so neither
 *  repeats the entry-to-panel-props mapping. */
export function SelectedSessionPanel({
  selected,
  slug,
  users,
  panelId,
  pickedHere,
  onClose,
  className,
  style,
}: Readonly<{
  selected: ScheduleEntry;
  slug: string;
  users: Record<string, User>;
  panelId: string;
  /** Whether the pick was made on this panel's surface: only that panel takes the focus and is
   *  the morph source, since both panels are mounted. */
  pickedHere: boolean;
  onClose: () => void;
  className?: string;
  /** Geometry a class cannot express: the desktop panel's own left edge, computed from the width
   *  the page gives the calendar. */
  style?: CSSProperties;
}>) {
  const { sessionId } = selected;
  const named = pickedHere && sessionId !== null;
  return (
    <SessionPanel
      entry={selected}
      id={panelId}
      label={selected.displayTitle}
      href={chipHref(selected, slug)}
      users={users}
      focusOnOpen={pickedHere}
      onClose={onClose}
      {...(className !== undefined && { className })}
      {...(style !== undefined && { style })}
      {...(named && { morphId: sessionMorphId(sessionId) })}
    />
  );
}

interface DayDetailProps {
  selectedDate: string;
  dayEntries: ScheduleEntry[];
  slug: string;
  onOpen: (entry: ScheduleEntry) => void;
  onSelect: (entry: ScheduleEntry) => void;
  /** The session panel state, grouped so it reads as one concern rather than four loose
   *  parameters. */
  panel: {
    selected: ScheduleEntry | null;
    users: Record<string, User>;
    panelId: string;
    pickedHere: boolean;
    onClose: () => void;
  };
}

/** Mobile-only panel listing the selected day's sessions, with the session panel for whichever
 *  one was tapped rendered underneath. */
export function DayDetail({
  selectedDate,
  dayEntries,
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
          slug={slug}
          users={panel.users}
          panelId={panel.panelId}
          pickedHere={panel.pickedHere}
          onClose={panel.onClose}
          className="mt-3"
        />
      )}
    </div>
  );
}
