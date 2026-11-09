import { describe, expect, it } from "vitest";

import { calendarSyncStatus } from "@/features/events/domain/calendar-sync-status";

const NOW = new Date("2027-01-13T12:00:00Z");

function subscription(
  lastFetchedAt: string | null,
  lastFetchedAgent: string | null = "Microsoft Office/16.0",
) {
  return { url: null, lastFetchedAt, lastFetchedAgent };
}

describe("calendarSyncStatus", () => {
  it("reads a fetch within the day as synced, naming the client", () => {
    expect(
      calendarSyncStatus(subscription("2027-01-13T10:00:00Z"), NOW),
    ).toEqual({
      state: "synced",
      label: "Synced 2 hours ago",
      provider: "Microsoft",
    });
  });

  it("reads a subscription nothing has fetched yet as waiting", () => {
    expect(calendarSyncStatus(subscription(null, null), NOW)).toEqual({
      state: "waiting",
      label: "Waiting for the first sync",
      provider: null,
    });
  });

  it("reads a fetch older than a day as stale", () => {
    expect(
      calendarSyncStatus(subscription("2027-01-10T12:00:00Z"), NOW),
    ).toEqual({
      state: "stale",
      label: "Last synced 3 days ago",
      provider: "Microsoft",
    });
  });

  it("names a well-known client by its product", () => {
    expect(
      calendarSyncStatus(
        subscription("2027-01-13T11:00:00Z", "Google-Calendar-Importer"),
        NOW,
      ).provider,
    ).toBe("Google Calendar");
  });

  it("leaves the provider out when no client was recorded", () => {
    expect(
      calendarSyncStatus(subscription("2027-01-13T11:00:00Z", null), NOW)
        .provider,
    ).toBeNull();
  });
});
