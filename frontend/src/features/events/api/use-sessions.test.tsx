import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys } from "@/features/events/api/query-keys";
import {
  useCreateOneOff,
  useEnsureOccurrence,
  useLastHeldSession,
  useRescheduleSession,
  useSessions,
  useSessionsHeldSince,
  useSkipOccurrence,
  useUnskipOccurrence,
  useUpcomingSessions,
} from "@/features/events/api/use-sessions";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    listUpcoming: vi.fn(),
    listPast: vi.fn(),
    ensureOccurrence: vi.fn(),
    skipOccurrence: vi.fn(),
    unskipOccurrence: vi.fn(),
    createOneOff: vi.fn(),
    updateSession: vi.fn(),
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

describe("useUpcomingSessions", () => {
  it("lists upcoming sessions", async () => {
    gateway.listUpcoming.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
    const { result } = renderHook(
      () => useUpcomingSessions("backend-guild", { page: 0, size: 20 }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useSessions", () => {
  it("returns the community's held sessions for labelling", async () => {
    // given
    gateway.listPast.mockResolvedValue({
      content: [{ id: 7 } as never],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
    });

    // when
    const { result } = renderHook(() => useSessions("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([{ id: 7 }]);
    expect(gateway.listPast.mock.calls[0]).toEqual([
      "backend-guild",
      { page: 0, size: 100 },
    ]);
  });
});

describe("useLastHeldSession", () => {
  it("asks for held sessions only, so a skipped occurrence can never answer", async () => {
    // given
    gateway.listPast.mockResolvedValue({
      content: [{ id: 7 } as never],
      totalElements: 12,
      totalPages: 12,
      number: 0,
      size: 1,
    });

    // when
    const { result } = renderHook(() => useLastHeldSession("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ id: 7 });
    expect(gateway.listPast.mock.calls[0]).toEqual([
      "backend-guild",
      { page: 0, size: 1, status: "HELD" },
    ]);
  });

  it("yields null when the community never held a session", async () => {
    // given
    gateway.listPast.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 1,
    });

    // when
    const { result } = renderHook(() => useLastHeldSession("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });
});

describe("useEnsureOccurrence", () => {
  it("calls ensureOccurrence with seriesId and occurrenceDate", async () => {
    gateway.ensureOccurrence.mockResolvedValue({ id: 9 } as never);
    const { result } = renderHook(() => useEnsureOccurrence("backend-guild"), {
      wrapper,
    });
    result.current.mutate({ seriesId: 1, occurrenceDate: "2026-06-19" });
    await waitFor(() =>
      expect(gateway.ensureOccurrence.mock.calls).toHaveLength(1),
    );
    expect(gateway.ensureOccurrence.mock.calls[0]).toEqual([1, "2026-06-19"]);
  });
});

describe("useSkipOccurrence", () => {
  it("calls skipOccurrence with seriesId and occurrenceDate", async () => {
    gateway.skipOccurrence.mockResolvedValue({
      id: 10,
      status: "SKIPPED",
    } as never);
    const { result } = renderHook(() => useSkipOccurrence("backend-guild"), {
      wrapper,
    });
    result.current.mutate({ seriesId: 1, occurrenceDate: "2026-06-26" });
    await waitFor(() =>
      expect(gateway.skipOccurrence.mock.calls).toHaveLength(1),
    );
    expect(gateway.skipOccurrence.mock.calls[0]).toEqual([1, "2026-06-26"]);
  });
});

describe("useUnskipOccurrence", () => {
  it("calls unskipOccurrence with seriesId and occurrenceDate", async () => {
    gateway.unskipOccurrence.mockResolvedValue({
      id: 10,
      status: "SCHEDULED",
    } as never);
    const { result } = renderHook(() => useUnskipOccurrence("backend-guild"), {
      wrapper,
    });
    result.current.mutate({ seriesId: 1, occurrenceDate: "2026-07-10" });
    await waitFor(() =>
      expect(gateway.unskipOccurrence.mock.calls).toHaveLength(1),
    );
    expect(gateway.unskipOccurrence.mock.calls[0]).toEqual([1, "2026-07-10"]);
  });

  it("invalidates the restored session's detail query so the banner clears", async () => {
    gateway.unskipOccurrence.mockResolvedValue({
      id: 10,
      status: "SCHEDULED",
    } as never);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useUnskipOccurrence("backend-guild"), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });
    result.current.mutate({ seriesId: 1, occurrenceDate: "2026-07-10" });
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: sessionKeys.detail(10),
      }),
    );
  });
});

describe("useCreateOneOff", () => {
  it("calls createOneOff with the slug and input", async () => {
    gateway.createOneOff.mockResolvedValue({ id: 11 } as never);
    const { result } = renderHook(() => useCreateOneOff("backend-guild"), {
      wrapper,
    });
    result.current.mutate({
      title: "One-off talk",
      scheduledAt: { date: "2026-06-25", time: "18:00", zone: "Europe/Paris" },
    });
    await waitFor(() =>
      expect(gateway.createOneOff.mock.calls).toHaveLength(1),
    );
    expect(gateway.createOneOff.mock.calls[0]?.[0]).toBe("backend-guild");
  });
});

describe("useRescheduleSession", () => {
  it("calls updateSession with the sessionId and input", async () => {
    gateway.updateSession.mockResolvedValue({ id: 9 } as never);
    const { result } = renderHook(() => useRescheduleSession("backend-guild"), {
      wrapper,
    });
    result.current.mutate({
      sessionId: 9,
      input: {
        scheduledAt: {
          date: "2026-07-01",
          time: "18:00",
          zone: "Europe/Paris",
        },
        durationMinutes: null,
        facilitatorUserId: null,
        place: EMPTY_PLACE,
      },
    });
    await waitFor(() =>
      expect(gateway.updateSession.mock.calls).toHaveLength(1),
    );
    expect(gateway.updateSession.mock.calls[0]?.[0]).toBe(9);
  });
});

describe("useSessionsHeldSince", () => {
  it("counts only the sessions that were actually held in the window", async () => {
    // given
    gateway.listPast.mockResolvedValue({
      content: [{ id: 7 } as never],
      totalElements: 4,
      totalPages: 4,
      number: 0,
      size: 1,
    });

    // when
    const { result } = renderHook(
      () => useSessionsHeldSince("backend-guild", "2026-04-01T00:00:00.000Z"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBe(4);
    expect(gateway.listPast.mock.calls[0]).toEqual([
      "backend-guild",
      { page: 0, size: 1, from: "2026-04-01T00:00:00.000Z", status: "HELD" },
    ]);
  });
});
