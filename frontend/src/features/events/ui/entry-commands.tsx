import {
  CalendarClock,
  CalendarOff,
  CircleX,
  Repeat,
  SkipForward,
  Undo2,
} from "lucide-react";

import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { hasPassed, timeLabel } from "@/features/events/domain/schedule";
import { shortDayLabel } from "@/features/events/ui/schedule-dates";
import type {
  Action,
  ActionGroup,
  MenuTitle,
} from "@/features/events/ui/ScheduleMenuList";
import { menuIconClass } from "@/features/events/ui/ScheduleMenuList";

/**
 * Skipping is for an occurrence of a series that has not happened yet. The server refuses it
 * outright on a past one (`OCCURRENCE_IN_PAST`), so offering it there would be an item that
 * looks live and answers with a conflict.
 */
function canSkipOccurrence(entry: ScheduleEntry, now: Date): boolean {
  return (
    entry.status === "SCHEDULED" &&
    entry.seriesId !== null &&
    entry.occurrenceDate !== null &&
    !hasPassed(entry, now)
  );
}

function canUnskipOccurrence(entry: ScheduleEntry): boolean {
  return (
    entry.status === "SKIPPED" &&
    entry.seriesId !== null &&
    entry.occurrenceDate !== null
  );
}

/**
 * Cancelling is what is left when skipping is not on offer: a one-off, which belongs to no series
 * and can never come back, or an occurrence the clock has already passed.
 *
 * Never both. Calling one session off is one decision, and a menu offering two words for it left
 * an organizer to work out that Skip is reversible and Cancel is not - a difference the labels do
 * not carry and nothing else in the product explained. Each occurrence now offers the one verb
 * that is true of it.
 */
function canCancelEntry(entry: ScheduleEntry, now: Date): boolean {
  return (
    entry.status === "SCHEDULED" &&
    entry.sessionId !== null &&
    !canSkipOccurrence(entry, now)
  );
}

/**
 * What this one date offers.
 *
 * Reschedule carries no trailing ellipsis: it opens the same dialog the session page's item does,
 * and the two menus should read identically.
 *
 * Reschedule is offered disabled rather than withheld while its editor is out of reach, because
 * the action exists either way and an item that comes and goes is harder to read than one that is
 * out. It is the only item that needs the row: the rest are named entirely by what the chip or
 * timeline row already carries, so a failed read costs the organizer none of them.
 */
function occurrenceActions(
  entry: ScheduleEntry,
  canReschedule: boolean,
  now: Date,
): Action[] {
  const actions: Action[] = [];
  if (entry.status === "SCHEDULED") {
    actions.push({
      id: "reschedule",
      label: "Reschedule",
      icon: <CalendarClock className={menuIconClass} />,
      isDisabled: !canReschedule,
    });
  }
  if (canSkipOccurrence(entry, now)) {
    actions.push({
      id: "skip",
      label: "Skip",
      icon: <SkipForward className={menuIconClass} />,
    });
  }
  if (canUnskipOccurrence(entry)) {
    actions.push({
      id: "unskip",
      label: "Unskip",
      icon: <Undo2 className={menuIconClass} />,
    });
  }
  if (canCancelEntry(entry, now)) {
    actions.push({
      id: "cancel",
      label: "Cancel",
      icon: <CircleX className={menuIconClass} />,
      tone: "danger",
    });
  }
  return actions;
}

/** What every date of the series offers. The verbs are bare because the group heading above them
 *  states the reach: shortening them under a rule alone would destroy the only thing that said
 *  these two commands are not about the occurrence that was clicked. */
function seriesActions(entry: ScheduleEntry): Action[] {
  if (entry.seriesId === null) return [];
  return [
    {
      id: "edit-series",
      label: "Edit",
      icon: <Repeat className={menuIconClass} />,
    },
    {
      id: "end-series",
      label: "End",
      icon: <CalendarOff className={menuIconClass} />,
      tone: "danger",
    },
  ];
}

/** The commands a right-click on an occurrence offers, split by how far each one reaches. The
 *  series group keeps its heading even when it is the only one left: a cancelled occurrence of a
 *  series offers nothing for the date itself, and a bare Edit and End would then say nothing at
 *  all about what they rewrite. */
export function entryGroups(
  entry: ScheduleEntry,
  canManage: boolean,
  canReschedule: boolean,
  now: Date,
): ActionGroup[] {
  if (!canManage) return [];
  return [
    {
      label: "This session",
      actions: occurrenceActions(entry, canReschedule, now),
    },
    { label: "Whole series", actions: seriesActions(entry) },
  ];
}

/**
 * What the menu says it was opened on: the name the chip or row already shows, over the day and
 * hour that distinguish this occurrence from the others carrying that same name.
 *
 * The day comes from {@link entryDayIso}, which is the rule that decided which cell the chip was
 * drawn in and which row the timeline dated, so the header names the day the reader clicked on by
 * construction. Reading `occurrenceDate` alone instead left a one-off - which is anchored to no
 * cadence and so has none - with a header stating a bare hour and no day whatsoever.
 */
export function entryTitle(entry: ScheduleEntry, label: string): MenuTitle {
  const iso = entryDayIso(entry);
  const parts = [
    iso === "" ? null : shortDayLabel(iso),
    timeLabel(entry),
  ].filter((part) => part !== null);
  return parts.length === 0
    ? { primary: label }
    : { primary: label, secondary: parts.join(" · ") };
}
