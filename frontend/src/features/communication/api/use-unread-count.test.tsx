import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpNotificationGateway } from "../adapters/http-notification-gateway";
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

function clientWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/notifications"]}>
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
}

function newClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

/** Mounts the hook under test alongside `useNavigate` so a single test can both read the
 *  query's state and drive a route change, without a second render tree outside the router. */
function useSubject() {
  return { query: useUnreadCount(), navigate: useNavigate() };
}

/** Every shell renders a bell in its mobile top bar and another in its desktop one - only one is
 *  visible per breakpoint, both are mounted - so two instances of this hook is the normal case,
 *  not an edge one. */
function useTwoBells() {
  useUnreadCount();
  return useSubject();
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useUnreadCount", () => {
  it("does not refetch on mount beyond the initial fetch", async () => {
    gateway.unreadCount.mockResolvedValue(2);

    const { result } = renderHook(() => useSubject(), {
      wrapper: clientWrapper(newClient()),
    });

    await waitFor(() => expect(result.current.query.isSuccess).toBe(true));
    expect(gateway.unreadCount).toHaveBeenCalledOnce();
  });

  it("refetches when the route's pathname changes", async () => {
    gateway.unreadCount.mockResolvedValue(2);

    const { result } = renderHook(() => useSubject(), {
      wrapper: clientWrapper(newClient()),
    });
    await waitFor(() => expect(result.current.query.isSuccess).toBe(true));
    gateway.unreadCount.mockClear();

    await act(() => result.current.navigate("/events"));

    await waitFor(() => expect(gateway.unreadCount).toHaveBeenCalledOnce());
  });

  // Two mounted bells means two invalidations in the same commit. `invalidateQueries` defaults to
  // `cancelRefetch: true`, so without the explicit override the second one silently cancels the
  // first fetch and issues another - and the cancelled request is already on the wire, since the
  // gateway does not forward the abort signal. That is a doubled request on every navigation of
  // every page in the app.
  it("issues one request per navigation however many bells are mounted", async () => {
    gateway.unreadCount.mockResolvedValue(2);

    const { result } = renderHook(() => useTwoBells(), {
      wrapper: clientWrapper(newClient()),
    });
    await waitFor(() => expect(result.current.query.isSuccess).toBe(true));
    gateway.unreadCount.mockClear();

    await act(() => result.current.navigate("/events"));

    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(gateway.unreadCount).toHaveBeenCalledOnce();
  });

  it("does not refetch when only the query string changes", async () => {
    gateway.unreadCount.mockResolvedValue(2);

    const { result } = renderHook(() => useSubject(), {
      wrapper: clientWrapper(newClient()),
    });
    await waitFor(() => expect(result.current.query.isSuccess).toBe(true));
    gateway.unreadCount.mockClear();

    await act(() => result.current.navigate("/notifications?tab=unread"));

    // Give any (incorrect) effect a chance to fire before asserting it didn't.
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(gateway.unreadCount).not.toHaveBeenCalled();
  });
});
