import { useEffect, useId, useState } from "react";

import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { presenterIdsOf } from "@/features/events/domain/schedule";
import { entryKey } from "@/features/events/ui/DayCell";

/**
 * Which of the two mounted panels a selection belongs to. The desktop grid and the phone day list
 * are told apart by a breakpoint and nothing else, so both panels sit in the document at once. The
 * surface the chip was on decides which panel carries the selection's id and takes the focus, so
 * neither ever answers for a chip the reader cannot see.
 */
export type PanelSurface = "desktop" | "phone";

export interface SessionSelection {
  entry: ScheduleEntry;
  surface: PanelSurface;
}

/** The same key a cell would compute for the selected entry, so the chip that was picked is the
 *  one that shows the ring. Undefined once the entry can no longer be placed on a date the grid
 *  actually holds it under. */
export function selectedKeyFor(
  selected: ScheduleEntry | null,
  byDate: Map<string, ScheduleEntry[]>,
): string | undefined {
  if (selected === null) return undefined;
  const iso = entryDayIso(selected);
  const idx = byDate.get(iso)?.indexOf(selected) ?? -1;
  return idx === -1 ? undefined : entryKey(selected, iso, idx);
}

/** Presenters to resolve for the panel: none until something is selected, so the calendar never
 *  fetches every presenter in the month. */
export function presenterIdsFor(selected: ScheduleEntry | null): string[] {
  return selected === null ? [] : presenterIdsOf(selected);
}

/** The DayCell prop for the chip that shows its selection ring, omitted entirely rather than
 *  passed as `undefined` (exactOptionalPropertyTypes). */
export function selectionProps(
  selectedKey: string | undefined,
): { selectedKey: string } | Record<string, never> {
  return selectedKey === undefined ? {} : { selectedKey };
}

/** A click on the chip that opened the panel, or inside the panel itself, is not "outside" it.
 *  Matched on attributes the panel and the chip carry for this purpose, never on a test id: a
 *  build that strips test ids would otherwise read a click inside the panel as a click away. */
function isInsideSelection(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false;
  if (target.closest("[data-session-panel]") !== null) return true;
  return target.closest("[data-chip]") !== null;
}

/**
 * The calendar's one selected chip, and everything that follows from it.
 *
 * The selection is held as the entry object itself, and a refreshed schedule mints a new object
 * for whichever entry moved while leaving every other one identical. A rescheduled, skipped or
 * cancelled entry therefore stops resolving to a key the grid holds, and the pick is read as a
 * pick of nothing rather than kept: the panel would otherwise go on stating the day and the hour
 * the entry had before the move, beside a chip visibly sitting on another day. Re-finding the
 * moved entry is deliberately not attempted - its identity is what changed, and guessing which
 * new object replaced it is how the wrong session gets shown.
 */
export function useSessionSelection(
  byDate: Map<string, ScheduleEntry[]>,
  monthKey: string,
) {
  const [picked, setPicked] = useState<SessionSelection | null>(null);
  const baseId = useId();

  const selectedKey = selectedKeyFor(picked?.entry ?? null, byDate);
  const selected = selectedKey === undefined ? null : picked;

  // The shown entries change with the month, and the previous month's answer is served as
  // placeholder data while the new one loads, so a selection can still resolve for a beat after
  // the reader has paged away from the chip it belongs to.
  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    setPicked(null);
  }, [monthKey]);

  useEffect(() => {
    if (selected === null) return;
    function onDown(event: MouseEvent) {
      if (!isInsideSelection(event.target)) setPicked(null);
    }
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [selected]);

  return {
    /** The selected entry itself, or null once nothing on the grid answers for the pick. */
    entry: selected?.entry ?? null,
    selectedKey,
    desktopPanelId: `${baseId}desktop`,
    phonePanelId: `${baseId}phone`,
    /** Whether the panel on this surface is the one the reader can see, and so the one to focus. */
    focusOn: (surface: PanelSurface) => selected?.surface === surface,
    select: (entry: ScheduleEntry, surface: PanelSurface) =>
      setPicked({ entry, surface }),
    close: () => setPicked(null),
  };
}
