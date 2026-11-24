/**
 * A session's held-on day, a bare calendar date the server reads on the session's
 * own clock. It parses as UTC midnight, so it is rendered in UTC: the day never
 * rolls back for a reader west of UTC.
 */
export function formatHeldOn(heldOn: string): string {
  return new Date(heldOn).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}
