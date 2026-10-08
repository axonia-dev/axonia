import { afterEach, describe, expect, it, vi } from "vitest";

import { jsonResponse, urlOf } from "@/test/fetch";

import { httpNotificationGateway } from "./http-notification-gateway";

describe("httpNotificationGateway", () => {
  afterEach(() => vi.restoreAllMocks());

  it("reads the first page without cursor parameters", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ entries: [], hasMore: false }));

    await httpNotificationGateway.page(undefined);

    expect(urlOf(spy.mock.calls[0]![0])).toContain(
      "/axonia/me/notifications?size=20",
    );
  });

  it("passes the cursor of the previous page", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse({ entries: [], hasMore: false }));

    await httpNotificationGateway.page({
      before: "2026-08-01T10:00:00Z",
      beforeId: 7,
    });

    expect(urlOf(spy.mock.calls[0]![0])).toContain(
      "/axonia/me/notifications?size=20&before=2026-08-01T10%3A00%3A00Z&beforeId=7",
    );
  });

  it("parses an entry", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({
        entries: [
          {
            id: 7,
            sender: "Frontend Guild",
            headline: "Frontend Dojo",
            line: "Held 1 August 2026 at 09:00",
            actionUrl: "https://axonia.example.com/s/1",
            sentAt: "2026-08-01T10:00:00Z",
            read: false,
          },
        ],
        hasMore: true,
      }),
    );

    const page = await httpNotificationGateway.page(undefined);

    expect(page.entries[0]?.headline).toBe("Frontend Dojo");
    expect(page.hasMore).toBe(true);
  });

  it("drops an entry whose shape it does not recognise rather than emptying the panel", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({
        entries: [{ id: 7, headline: 42 }],
        hasMore: false,
      }),
    );

    const page = await httpNotificationGateway.page(undefined);

    expect(page.entries).toHaveLength(0);
  });

  // The cursor survives the drop that empties the entries, because it is the server's own statement of where the
  // window ended rather than anything read off the rows that parsed. Losing it here would end the list at the first
  // wholly-unreadable window, whatever the hook then did with it.
  it("keeps the server's cursor even when every entry of the window was dropped", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({
        entries: [{ id: 7, headline: 42 }],
        hasMore: true,
        nextBefore: "2026-08-01T10:00:00Z",
        nextBeforeId: 7,
      }),
    );

    const page = await httpNotificationGateway.page(undefined);

    expect(page.entries).toHaveLength(0);
    expect(page.nextBefore).toBe("2026-08-01T10:00:00Z");
    expect(page.nextBeforeId).toBe(7);
  });

  it("reads the unread count", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({ count: 3 }));

    expect(await httpNotificationGateway.unreadCount()).toBe(3);
  });

  it("marks one read", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await httpNotificationGateway.markRead(7);

    expect(urlOf(spy.mock.calls[0]![0])).toContain(
      "/axonia/me/notifications/7/read",
    );
    expect(spy.mock.calls[0]![1]?.method).toBe("POST");
  });

  it("marks all read", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await httpNotificationGateway.markAllRead();

    expect(urlOf(spy.mock.calls[0]![0])).toContain(
      "/axonia/me/notifications/read-all",
    );
    expect(spy.mock.calls[0]![1]?.method).toBe("POST");
  });
});
