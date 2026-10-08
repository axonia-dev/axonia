import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpNotificationGateway } from "../adapters/http-notification-gateway";
import { useMarkAllRead, useNotifications } from "./use-notifications";
import { useUnreadCount } from "./use-unread-count";

vi.mock("../adapters/http-notification-gateway", () => ({
  httpNotificationGateway: {
    page: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}));

const gateway = vi.mocked(httpNotificationGateway);

function entry(id: number, sentAt: string) {
  return {
    id,
    sender: "Frontend Guild",
    headline: "Frontend Dojo",
    line: "Held 1 August 2026 at 09:00",
    actionUrl: "https://axonia.example.com/s/1",
    sentAt,
    read: false,
  };
}

function clientWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

function newClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useNotifications", () => {
  it("asks for the first page with no cursor", async () => {
    gateway.page.mockResolvedValue({ entries: [], hasMore: false });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: clientWrapper(newClient()),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.page).toHaveBeenCalledWith(undefined);
  });

  it("pages back from the cursor the server handed it", async () => {
    gateway.page.mockResolvedValueOnce({
      entries: [
        entry(9, "2026-08-03T10:00:00Z"),
        entry(8, "2026-08-02T10:00:00Z"),
      ],
      hasMore: true,
      nextBefore: "2026-08-02T10:00:00Z",
      nextBeforeId: 8,
    });
    gateway.page.mockResolvedValueOnce({ entries: [], hasMore: false });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: clientWrapper(newClient()),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await result.current.fetchNextPage();

    expect(gateway.page).toHaveBeenLastCalledWith({
      before: "2026-08-02T10:00:00Z",
      beforeId: 8,
    });
  });

  it("stops paging when the server says nothing remains", async () => {
    gateway.page.mockResolvedValue({
      entries: [entry(9, "2026-08-03T10:00:00Z")],
      hasMore: false,
    });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: clientWrapper(newClient()),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.hasNextPage).toBe(false);
  });

  // A window whose every row carried a message type this deploy does not declare comes back empty with more
  // genuinely behind it. Deriving the cursor from the last visible entry found none, paging stopped there, and every
  // older readable entry was stranded behind that one unreadable window for good. The cursor is the server's.
  it("pages past a window whose every entry was dropped", async () => {
    gateway.page.mockResolvedValueOnce({
      entries: [],
      hasMore: true,
      nextBefore: "2026-08-03T10:00:00Z",
      nextBeforeId: 9,
    });
    gateway.page.mockResolvedValueOnce({
      entries: [entry(8, "2026-08-02T10:00:00Z")],
      hasMore: false,
    });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: clientWrapper(newClient()),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.hasNextPage).toBe(true);
    await result.current.fetchNextPage();

    expect(gateway.page).toHaveBeenLastCalledWith({
      before: "2026-08-03T10:00:00Z",
      beforeId: 9,
    });
    await waitFor(() =>
      expect(result.current.data?.pages.at(-1)?.entries).toHaveLength(1),
    );
  });

  // hasMore without a cursor cannot be acted on: re-asking with no cursor re-serves page 1 forever. Stopping is the
  // only safe reading of a response that says more remain but not where they start.
  it("stops paging when the server claims more remain but hands back no cursor", async () => {
    gateway.page.mockResolvedValue({ entries: [], hasMore: true });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: clientWrapper(newClient()),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.hasNextPage).toBe(false);
  });
});

describe("useMarkAllRead", () => {
  it("makes a live notification list and unread-count observer refetch once the mark settles", async () => {
    gateway.page.mockResolvedValue({ entries: [], hasMore: false });
    gateway.unreadCount.mockResolvedValueOnce(3).mockResolvedValueOnce(0);
    gateway.markAllRead.mockResolvedValue(undefined);

    const client = newClient();
    const wrapper = clientWrapper(client);

    const { result: list } = renderHook(() => useNotifications(), {
      wrapper,
    });
    const { result: unread } = renderHook(() => useUnreadCount().data, {
      wrapper,
    });
    await waitFor(() => expect(list.current.isSuccess).toBe(true));
    await waitFor(() => expect(unread.current).toBe(3));
    gateway.page.mockClear();

    const { result: mutation } = renderHook(() => useMarkAllRead(), {
      wrapper,
    });
    await mutation.current.mutateAsync();

    // The mark call itself happened...
    expect(gateway.markAllRead).toHaveBeenCalledOnce();
    // ...and both live observers were made stale and actually refetched, not just
    // told they were stale: the list re-ran its query function, and the badge
    // picked up the fresh unread count from the server.
    await waitFor(() => expect(gateway.page).toHaveBeenCalledOnce());
    await waitFor(() => expect(unread.current).toBe(0));
  });
});
