/**
 * Pure lookup derived from the backend's list of active `YYYY-MM` months:
 * which months the picker dots. `monthIndex0` is 0-based (January = 0) to
 * match the calendar. Activity is a hint about where the content is, never a
 * bound on where the user may navigate: empty years must stay reachable so a
 * new community can backfill past sessions and plan future ones.
 */
export type ActivityLookup = (year: number, monthIndex0: number) => boolean;

export function activityLookup(months: string[]): ActivityLookup {
  const set = new Set(months);
  return (year, monthIndex0) =>
    set.has(`${year}-${String(monthIndex0 + 1).padStart(2, "0")}`);
}
