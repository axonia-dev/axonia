import {
  agentLabel,
  type CalendarSubscription,
  DAY,
  elapsed,
} from "@/features/events/domain/calendar-subscription";

type CalendarSyncState = "synced" | "waiting" | "stale";

export interface CalendarSyncStatus {
  state: CalendarSyncState;
  label: string;
  provider: string | null;
}

/** A provider polls well within a day, so a fetch older than that is worth a second look. */
export function calendarSyncStatus(
  { lastFetchedAt, lastFetchedAgent }: CalendarSubscription,
  now: Date,
): CalendarSyncStatus {
  const provider =
    lastFetchedAgent === null ? null : agentLabel(lastFetchedAgent);
  if (lastFetchedAt === null) {
    return { state: "waiting", label: "Waiting for the first sync", provider };
  }
  const delta = now.getTime() - new Date(lastFetchedAt).getTime();
  const when = elapsed(delta);
  return delta < DAY
    ? { state: "synced", label: `Synced ${when}`, provider }
    : { state: "stale", label: `Last synced ${when}`, provider };
}
