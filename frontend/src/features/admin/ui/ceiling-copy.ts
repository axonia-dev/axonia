// The privilege ceiling's user-facing strings, in one place so the wording cannot drift
// between the role pickers, the role menu, the drawer, and the permission catalog.

export const REACHES_BEYOND = "Reaches beyond what this account holds.";

export const REQUIRES_HOLDING = "Requires holding this capability.";

export function lockedCountLine(locked: number, total: number): string {
  return `${locked} of ${total} capabilities are locked. Changing one requires already holding it.`;
}
