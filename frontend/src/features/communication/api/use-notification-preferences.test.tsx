import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpNotificationPreferenceGateway } from "@/features/communication/adapters/http-notification-preference-gateway";
import { communicationKeys } from "@/features/communication/api/query-keys";
import {
  useNotificationPreferences,
  useSetNotificationPreference,
} from "@/features/communication/api/use-notification-preferences";
import type {
  MessageType,
  NotificationPreference,
} from "@/features/communication/domain/notification-preference";

vi.mock(
  "@/features/communication/adapters/http-notification-preference-gateway",
  () => ({
    httpNotificationPreferenceGateway: {
      getMine: vi.fn(),
      setMine: vi.fn(),
    },
  }),
);

const gateway = vi.mocked(httpNotificationPreferenceGateway);

function clientWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

const SEED_PREFERENCES: NotificationPreference[] = [
  { type: "session_upcoming", enabled: true },
  { type: "session_wrap_up", enabled: true },
];

function seededClient() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  client.setQueryData(
    communicationKeys.notificationPreferences(),
    SEED_PREFERENCES,
  );
  return client;
}

function cachedPreference(client: QueryClient, type: MessageType) {
  return client
    .getQueryData<NotificationPreference[]>(
      communicationKeys.notificationPreferences(),
    )
    ?.find((preference) => preference.type === type)?.enabled;
}

beforeEach(() => {
  vi.clearAllMocks();
  // No test in this file mounts a `useNotificationPreferences` observer alongside the
  // mutation, so `onSettled`'s invalidate never actually refetches - but if a later test
  // adds one, it must see the same state the cache was seeded with, not `undefined`.
  gateway.getMine.mockResolvedValue(SEED_PREFERENCES);
});

describe("useNotificationPreferences", () => {
  it("returns the fetched preferences", async () => {
    gateway.getMine.mockResolvedValue([
      { type: "session_upcoming", enabled: false },
    ]);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useNotificationPreferences(), {
      wrapper: clientWrapper(client),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([
      { type: "session_upcoming", enabled: false },
    ]);
  });
});

describe("useSetNotificationPreference", () => {
  it("flips the cached switch immediately, before the server responds", async () => {
    // given a slow server
    const client = seededClient();
    let resolve: () => void = () => {};
    gateway.setMine.mockReturnValue(
      new Promise((r) => {
        resolve = () => r(undefined);
      }),
    );
    const { result } = renderHook(
      () => useSetNotificationPreference("session_wrap_up"),
      { wrapper: clientWrapper(client) },
    );

    // when the member turns wrap-up mail off
    act(() => result.current.mutate(false));

    // then the cache reflects it right away, with the server still pending
    await waitFor(() => {
      expect(cachedPreference(client, "session_wrap_up")).toBe(false);
    });
    act(() => resolve());
  });

  it("rolls the cache back when the server rejects the change", async () => {
    // given a server that refuses
    const client = seededClient();
    gateway.setMine.mockRejectedValue(new Error("nope"));
    const { result } = renderHook(
      () => useSetNotificationPreference("session_wrap_up"),
      { wrapper: clientWrapper(client) },
    );

    // when the change fails
    act(() => result.current.mutate(false));

    // then the cache snaps back to the last known-good state
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cachedPreference(client, "session_wrap_up")).toBe(true);
  });

  it("rolls back only the failed switch, leaving a different switch's in-flight toggle alone", async () => {
    // given switch A (session_upcoming) stuck talking to a slow server, and switch B
    // (session_wrap_up) its own independent mutation, each with its own scope
    const client = seededClient();
    let rejectA: (error: Error) => void = () => {};
    gateway.setMine.mockImplementation((type) => {
      if (type === "session_upcoming") {
        return new Promise((_resolve, reject) => {
          rejectA = reject;
        });
      }
      return Promise.resolve(undefined);
    });
    const { result: resultA } = renderHook(
      () => useSetNotificationPreference("session_upcoming"),
      { wrapper: clientWrapper(client) },
    );
    const { result: resultB } = renderHook(
      () => useSetNotificationPreference("session_wrap_up"),
      { wrapper: clientWrapper(client) },
    );

    // when A is turned off first
    act(() => resultA.current.mutate(false));
    await waitFor(() => {
      expect(cachedPreference(client, "session_upcoming")).toBe(false);
    });

    // and B is turned off before A has settled
    act(() => resultB.current.mutate(false));
    await waitFor(() => {
      expect(cachedPreference(client, "session_wrap_up")).toBe(false);
    });

    // when A's save fails
    act(() => rejectA(new Error("nope")));

    // then only A's row rolls back...
    await waitFor(() => {
      expect(cachedPreference(client, "session_upcoming")).toBe(true);
    });

    // ...and B's optimistic value is untouched by A's rollback
    expect(cachedPreference(client, "session_wrap_up")).toBe(false);
  });

  it("serializes back-to-back PUTs on the same switch, so last click wins on the server too", async () => {
    // given the first PUT still talking to the server
    const client = seededClient();
    const calls: boolean[] = [];
    let resolveFirst: () => void = () => {};
    gateway.setMine.mockImplementation((_type, enabled) => {
      calls.push(enabled);
      if (calls.length === 1) {
        return new Promise((resolve) => {
          resolveFirst = () => resolve(undefined);
        });
      }
      return Promise.resolve(undefined);
    });
    const { result } = renderHook(
      () => useSetNotificationPreference("session_upcoming"),
      { wrapper: clientWrapper(client) },
    );

    // when the member clicks the same switch twice in a row
    act(() => result.current.mutate(false));
    await waitFor(() => expect(calls).toEqual([false]));
    act(() => result.current.mutate(true));

    // then the second click's PUT does not fire while the first is still outstanding...
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(calls).toEqual([false]);

    // ...only once the first settles
    act(() => resolveFirst());
    await waitFor(() => expect(calls).toEqual([false, true]));
  });

  it("does not hold back a different switch's PUT while one switch is stuck", async () => {
    // given switch A's PUT never resolving in this test (simulating a hung request)
    const client = seededClient();
    const calls: MessageType[] = [];
    gateway.setMine.mockImplementation((type) => {
      calls.push(type);
      if (type === "session_upcoming") {
        return new Promise(() => {
          /* never settles */
        });
      }
      return Promise.resolve(undefined);
    });
    const { result: resultA } = renderHook(
      () => useSetNotificationPreference("session_upcoming"),
      { wrapper: clientWrapper(client) },
    );
    const { result: resultB } = renderHook(
      () => useSetNotificationPreference("session_wrap_up"),
      { wrapper: clientWrapper(client) },
    );

    // when A is clicked and never settles, then B is clicked
    act(() => resultA.current.mutate(false));
    await waitFor(() => expect(calls).toEqual(["session_upcoming"]));
    act(() => resultB.current.mutate(false));

    // then B's PUT still fires, unblocked by A's own scope
    await waitFor(() =>
      expect(calls).toEqual(["session_upcoming", "session_wrap_up"]),
    );
  });
});
