import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { actionItemKeys } from "@/features/events/api/query-keys";
import {
  useCancelSession,
  useCloseSession,
  useMessagePreview,
  useRecordFollowUp,
  useSession,
  useSetUpcomingRelease,
  useSetWrapUpRelease,
} from "@/features/events/api/use-session";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    getSession: vi.fn(),
    cancelSession: vi.fn(),
    closeSession: vi.fn(),
    setWrapUpRelease: vi.fn(),
    setUpcomingRelease: vi.fn(),
    getMessagePreview: vi.fn(),
    recordFollowUp: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useSession", () => {
  it("fetches a single session by id", async () => {
    gateway.getSession.mockResolvedValue({
      id: 9,
      status: "SCHEDULED",
    } as never);
    const { result } = renderHook(() => useSession(9), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ id: 9, status: "SCHEDULED" });
    expect(gateway.getSession.mock.calls[0]).toEqual([9]);
  });

  /**
   * A null id is a projected ghost, and there is no row to read. `GET /sessions/null` is the shape
   * of the bug this arm exists to make unreachable, so a refetch is asked for explicitly: an
   * `enabled` flag would stop the first fetch and let that one through.
   */
  it("asks for nothing at all when the occurrence has no row yet", async () => {
    const { result } = renderHook(() => useSession(null), { wrapper });

    expect(gateway.getSession).not.toHaveBeenCalled();
    expect(result.current.data).toBeUndefined();

    await result.current.refetch();

    expect(gateway.getSession).not.toHaveBeenCalled();
  });
});

describe("useCancelSession", () => {
  it("cancels the session by id", async () => {
    gateway.cancelSession.mockResolvedValue({
      id: 9,
      status: "CANCELLED",
    } as never);
    const { result } = renderHook(() => useCancelSession("backend-guild"), {
      wrapper,
    });
    result.current.mutate(9);
    await waitFor(() =>
      expect(gateway.cancelSession.mock.calls).toHaveLength(1),
    );
    expect(gateway.cancelSession.mock.calls[0]).toEqual([9]);
  });
});

describe("useCloseSession", () => {
  it("posts the organizer's held/not-held answer for the session", async () => {
    gateway.closeSession.mockResolvedValue({ id: 9, status: "HELD" } as never);
    const { result } = renderHook(() => useCloseSession("backend-guild"), {
      wrapper,
    });
    result.current.mutate({ sessionId: 9, input: { held: true } });
    await waitFor(() =>
      expect(gateway.closeSession.mock.calls).toHaveLength(1),
    );
    expect(gateway.closeSession.mock.calls[0]).toEqual([9, { held: true }]);
  });
});

describe("useSetWrapUpRelease", () => {
  it("PUTs the new release instant for the session", async () => {
    gateway.setWrapUpRelease.mockResolvedValue({
      id: 9,
      status: "HELD",
    } as never);
    const { result } = renderHook(() => useSetWrapUpRelease("backend-guild"), {
      wrapper,
    });
    result.current.mutate({
      sessionId: 9,
      input: { releaseAt: "2026-03-12T09:00:00Z" },
    });
    await waitFor(() =>
      expect(gateway.setWrapUpRelease.mock.calls).toHaveLength(1),
    );
    expect(gateway.setWrapUpRelease.mock.calls[0]).toEqual([
      9,
      { releaseAt: "2026-03-12T09:00:00Z" },
    ]);
  });

  it("sends an explicit null to suppress the wrap-up permanently", async () => {
    gateway.setWrapUpRelease.mockResolvedValue({
      id: 9,
      status: "HELD",
    } as never);
    const { result } = renderHook(() => useSetWrapUpRelease("backend-guild"), {
      wrapper,
    });
    result.current.mutate({ sessionId: 9, input: { releaseAt: null } });
    await waitFor(() =>
      expect(gateway.setWrapUpRelease.mock.calls).toHaveLength(1),
    );
    expect(gateway.setWrapUpRelease.mock.calls[0]).toEqual([
      9,
      { releaseAt: null },
    ]);
  });
});

describe("useSetUpcomingRelease", () => {
  it("hands the reminder's payload through untouched, whichever of the three it is", async () => {
    gateway.setUpcomingRelease.mockResolvedValue({
      id: 9,
      status: "SCHEDULED",
    } as never);
    const { result } = renderHook(
      () => useSetUpcomingRelease("backend-guild"),
      {
        wrapper,
      },
    );

    result.current.mutate({ sessionId: 9, input: { sendNow: true } });

    await waitFor(() =>
      expect(gateway.setUpcomingRelease.mock.calls).toHaveLength(1),
    );
    // The hook adds nothing of its own: no browser instant standing in for "now", and no
    // releaseAt key invented for the empty payload.
    expect(gateway.setUpcomingRelease.mock.calls[0]).toEqual([
      9,
      { sendNow: true },
    ]);
  });
});

describe("useMessagePreview", () => {
  function sharedClientWrapper() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    return function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      );
    };
  }

  it("asks the gateway for the named message type", async () => {
    gateway.getMessagePreview.mockResolvedValue({
      subject: "s",
      html: "<p>h</p>",
    });
    const { result } = renderHook(
      () => useMessagePreview("backend-guild", 9, "session_upcoming", true),
      { wrapper },
    );

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(gateway.getMessagePreview.mock.calls[0]).toEqual([
      "backend-guild",
      9,
      "session_upcoming",
    ]);
  });

  it("sends no request while the dialog holding it is closed", () => {
    renderHook(
      () => useMessagePreview("backend-guild", 9, "session_wrap_up", false),
      { wrapper },
    );

    expect(gateway.getMessagePreview).not.toHaveBeenCalled();
  });

  // The two previews of one session are two different renders. A cache key naming only the
  // session would serve whichever dialog opened second the other one's mail - a wrong-content
  // bug that looks like a working feature, which is exactly why the message type is in the key.
  it("keeps the two message types apart in the cache, for the same session", async () => {
    gateway.getMessagePreview.mockImplementation((_slug, _id, type) =>
      Promise.resolve({ subject: type, html: `<p>${type}</p>` }),
    );
    const shared = sharedClientWrapper();

    const wrapUp = renderHook(
      () => useMessagePreview("backend-guild", 9, "session_wrap_up", true),
      { wrapper: shared },
    );
    await waitFor(() => expect(wrapUp.result.current.data).toBeDefined());
    const upcoming = renderHook(
      () => useMessagePreview("backend-guild", 9, "session_upcoming", true),
      { wrapper: shared },
    );
    await waitFor(() => expect(upcoming.result.current.data).toBeDefined());

    expect(wrapUp.result.current.data?.subject).toBe("session_wrap_up");
    expect(upcoming.result.current.data?.subject).toBe("session_upcoming");
    expect(gateway.getMessagePreview).toHaveBeenCalledTimes(2);
  });
});

describe("useRecordFollowUp", () => {
  it("sends the recording and the note the organizer captured", async () => {
    // given
    gateway.recordFollowUp.mockResolvedValue({ id: 7 } as never);
    const { result } = renderHook(() => useRecordFollowUp("guild", 7), {
      wrapper,
    });

    // when
    result.current.mutate({
      notes: "The p95 numbers landed.",
      recordingUrl: "https://meet.example/rec/7",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.recordFollowUp).toHaveBeenCalledWith(7, {
      notes: "The p95 numbers landed.",
      recordingUrl: "https://meet.example/rec/7",
    });
  });

  /**
   * The pass carries, closes and drops the session's checkpoints, so every action-item read is
   * stale the moment it lands. Nothing refetches on its own, and a dialog reopened on the stale
   * page answers a second time for items no longer pinned there, which the server refuses and
   * rolls the recording and the note back with.
   */
  it("invalidates the community's action items the pass just settled", async () => {
    // given
    gateway.recordFollowUp.mockResolvedValue({ id: 7 } as never);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRecordFollowUp("guild", 7), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });

    // when
    result.current.mutate({ notes: "", recordingUrl: "", decisions: [] });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: actionItemKeys.community("guild"),
    });
  });
});
