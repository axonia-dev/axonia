import type { ActionItemOutcome } from "@/features/events/domain/action-item";

/** "Sep 11" - the one spelling of a due date, so the Action items tab and a session's action point
 *  row cannot word the same day two ways. Read in UTC because a due date is a civil date: a local
 *  reading renders the day before for every viewer west of UTC. */
export function dueDateLabel(dueDate: string): string {
  return new Date(dueDate).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/** "Carried once" / "Carried N times" - a count of `CARRIED` checkpoint rows, never a counter
 *  field on the item. Null on a fresh item: silence there reads as "never carried", not zero.
 *  Shared so the row and the Action items tab cannot word the same fact differently. */
export function carryLabel(carryCount: number): string | null {
  if (carryCount <= 0) return null;
  return carryCount === 1 ? "Carried once" : `Carried ${carryCount} times`;
}

/** The one spelling of each outcome. "Won't do" rather than "Dropped": the record says the
 *  community settled this as work it will not do, which is a decision, where "dropped" sounds
 *  like something that fell off the list on its own. Shared so the row, the edit dialog, the
 *  session's follow-up and the settled list cannot name the same outcome four ways. */
export function outcomeLabel(outcome: ActionItemOutcome): string {
  if (outcome === "DONE") return "Done";
  if (outcome === "DROPPED") return "Won’t do";
  return "Open";
}
