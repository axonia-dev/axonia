import { describe, expect, it } from "vitest";

import {
  calendarSubscriptionSchema,
  calendarTargets,
  formatLastSync,
  webcalUrl,
} from "@/features/events/domain/calendar-subscription";

const NOW = new Date("2026-08-15T10:00:00Z");
const WEBCAL = "webcal://axonia.example.com/public/calendar/abc123.ics";

function synced(lastFetchedAt: string | null, lastFetchedAgent: string | null) {
  return { url: null, lastFetchedAt, lastFetchedAgent };
}

describe("calendarSubscriptionSchema", () => {
  it("parses a freshly minted subscription", () => {
    const parsed = calendarSubscriptionSchema.parse({
      url: "https://axonia.example.com/public/calendar/abc123.ics",
      lastFetchedAt: null,
      lastFetchedAgent: null,
    });

    expect(parsed.url).toBe(
      "https://axonia.example.com/public/calendar/abc123.ics",
    );
  });

  it("parses an existing subscription, which carries no URL", () => {
    const parsed = calendarSubscriptionSchema.parse({
      url: null,
      lastFetchedAt: "2026-08-15T08:00:00Z",
      lastFetchedAgent: "Outlook/16.0",
    });

    expect(parsed.url).toBeNull();
    expect(parsed.lastFetchedAgent).toBe("Outlook/16.0");
  });
});

describe("webcalUrl", () => {
  it("hands the feed over under the scheme calendars subscribe to", () => {
    expect(
      webcalUrl("https://axonia.example.com/public/calendar/abc123.ics"),
    ).toBe(WEBCAL);
  });

  it("converts a plain-http base too, which is what local development serves", () => {
    expect(webcalUrl("http://localhost:8080/public/calendar/abc123.ics")).toBe(
      "webcal://localhost:8080/public/calendar/abc123.ics",
    );
  });
});

describe("calendarTargets", () => {
  const targets = calendarTargets(WEBCAL, "Frontend Dojo");
  const encoded = encodeURIComponent(WEBCAL);

  it("hands Google the feed as its cid", () => {
    expect(targets[0]).toEqual({
      id: "google",
      label: "Google",
      href: `https://calendar.google.com/calendar/r?cid=${encoded}`,
    });
  });

  it("hands Outlook the feed and the name to file it under", () => {
    expect(targets[1]?.href).toBe(
      `https://outlook.office.com/calendar/0/addfromweb?url=${encoded}` +
        `&name=${encodeURIComponent("Frontend Dojo")}`,
    );
  });

  it("hands Apple and any other client the feed itself", () => {
    expect(targets[2]?.label).toBe("Apple");
    expect(targets[2]?.href).toBe(WEBCAL);
    expect(targets[3]?.label).toBe("Any other");
    expect(targets[3]?.href).toBe(WEBCAL);
  });
});

describe("formatLastSync", () => {
  /** No sync is no evidence, and the line exists only to carry evidence. */
  it("has no line to report before the first fetch", () => {
    expect(formatLastSync(synced(null, null), NOW)).toBeNull();
  });

  it("reports whole hours and the client that fetched", () => {
    expect(
      formatLastSync(synced("2026-08-15T08:00:00Z", "Outlook/16.0"), NOW),
    ).toBe("Last synced 2 hours ago, by Outlook");
  });

  it("reports minutes within the hour", () => {
    expect(formatLastSync(synced("2026-08-15T09:59:10Z", null), NOW)).toBe(
      "Last synced just now",
    );
    expect(formatLastSync(synced("2026-08-15T09:59:00Z", null), NOW)).toBe(
      "Last synced 1 minute ago",
    );
    expect(formatLastSync(synced("2026-08-15T09:20:00Z", null), NOW)).toBe(
      "Last synced 40 minutes ago",
    );
  });

  it("reports days past the first one", () => {
    expect(formatLastSync(synced("2026-08-12T10:00:00Z", null), NOW)).toBe(
      "Last synced 3 days ago",
    );
  });

  it("names the well-known calendar clients rather than their user agent", () => {
    const label = (agent: string) =>
      formatLastSync(synced("2026-08-15T09:00:00Z", agent), NOW);

    expect(label("Google-Calendar-Importer")).toContain("by Google Calendar");
    expect(label("Mozilla/5.0 (Macintosh) CalendarAgent/950.5")).toContain(
      "by Apple Calendar",
    );
    expect(label("Mozilla/5.0 Thunderbird/128.2")).toContain("by Thunderbird");
  });

  it("keeps only the product token of an unrecognised agent", () => {
    const line = formatLastSync(
      synced(
        "2026-08-15T09:00:00Z",
        "SomeCalendarClient/9.1 (a long build banner nobody needs)",
      ),
      NOW,
    );

    expect(line).toBe("Last synced 1 hour ago, by SomeCalendarClient/9.1");
  });

  it("caps a product token long enough to overrun the line", () => {
    const line = formatLastSync(
      synced("2026-08-15T09:00:00Z", "z".repeat(60)),
      NOW,
    );

    expect(line).toBe(`Last synced 1 hour ago, by ${"z".repeat(40)}…`);
  });

  it("omits the client when none was recorded", () => {
    expect(formatLastSync(synced("2026-08-15T09:00:00Z", null), NOW)).toBe(
      "Last synced 1 hour ago",
    );
  });
});
