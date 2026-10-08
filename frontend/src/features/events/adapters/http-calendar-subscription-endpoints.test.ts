import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { jsonResponse, urlOf } from "@/test/fetch";

const FEED_URL = "https://axonia.example.com/public/calendar/abc123.ics";
const PATH = "/axonia/communities/frontend-dojo/calendar-subscription";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("httpEventsGateway: calendar subscription endpoints", () => {
  it("getCalendarSubscription GETs the community's subscription", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        url: null,
        lastFetchedAt: "2026-08-15T08:00:00Z",
        lastFetchedAgent: "Outlook/16.0",
      }),
    );

    const subscription =
      await httpEventsGateway.getCalendarSubscription("frontend-dojo");

    expect(subscription?.lastFetchedAgent).toBe("Outlook/16.0");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(PATH);
    expect(call?.[1]?.method).toBeUndefined();
  });

  // Never having subscribed is an answer the dialog acts on, not a failure it reports.
  it("getCalendarSubscription answers null when the member has never subscribed", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ detail: "Calendar not found" }, 404),
    );

    await expect(
      httpEventsGateway.getCalendarSubscription("frontend-dojo"),
    ).resolves.toBeNull();
  });

  it("getCalendarSubscription still reports a real failure", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ detail: "Boom" }, 500));

    await expect(
      httpEventsGateway.getCalendarSubscription("frontend-dojo"),
    ).rejects.toThrow();
  });

  it("subscribeToCalendar POSTs to the community's subscription and returns the minted URL", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        url: FEED_URL,
        lastFetchedAt: null,
        lastFetchedAgent: null,
      }),
    );

    const subscription =
      await httpEventsGateway.subscribeToCalendar("frontend-dojo");

    expect(subscription.url).toBe(FEED_URL);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(PATH);
    expect(call?.[1]?.method).toBe("POST");
  });

  it("subscribeToCalendar returns the fetch stamp of an existing subscription, which has no URL", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        url: null,
        lastFetchedAt: "2026-08-15T08:00:00Z",
        lastFetchedAgent: "Outlook/16.0",
      }),
    );

    const subscription =
      await httpEventsGateway.subscribeToCalendar("frontend-dojo");

    expect(subscription.url).toBeNull();
    expect(subscription.lastFetchedAt).toBe("2026-08-15T08:00:00Z");
    expect(subscription.lastFetchedAgent).toBe("Outlook/16.0");
  });

  it("resetCalendarSubscription POSTs to the reset path and returns a fresh URL", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        url: FEED_URL,
        lastFetchedAt: null,
        lastFetchedAgent: null,
      }),
    );

    const subscription =
      await httpEventsGateway.resetCalendarSubscription("frontend-dojo");

    expect(subscription.url).toBe(FEED_URL);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(`${PATH}/reset`);
    expect(call?.[1]?.method).toBe("POST");
  });

  // A second content type at a call site is appended rather than replaced, and the server
  // answers 415. Neither of these two calls carries a body, so neither states one at all.
  it("sends no body and states the content type exactly once", async () => {
    // A fresh Response per call: a body can only be read once.
    vi.mocked(fetch).mockImplementation(() =>
      Promise.resolve(
        jsonResponse({
          url: FEED_URL,
          lastFetchedAt: null,
          lastFetchedAgent: null,
        }),
      ),
    );

    await httpEventsGateway.subscribeToCalendar("frontend-dojo");
    await httpEventsGateway.resetCalendarSubscription("frontend-dojo");

    for (const call of vi.mocked(fetch).mock.calls) {
      expect(call[1]?.body).toBeUndefined();
      const headers = call[1]?.headers as Record<string, string>;
      const contentTypes = Object.keys(headers).filter((name) =>
        /^content-type$/i.test(name),
      );
      expect(contentTypes).toEqual(["content-type"]);
    }
  });
});
