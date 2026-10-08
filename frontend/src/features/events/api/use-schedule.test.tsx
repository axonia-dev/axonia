import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useSchedule } from "@/features/events/api/use-schedule";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    getSchedule: vi.fn(),
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

const entry: ScheduleEntry = {
  sessionId: 9,
  seriesId: 1,
  title: null,
  occurrenceDate: "2026-06-19",
  scheduledAt: "2026-06-19T18:00:00Z",
  hasTime: true,
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  status: "SCHEDULED",
  virtual: false,
  place: EMPTY_PLACE,
  sections: [],
};

describe("useSchedule", () => {
  it("fetches schedule entries for the given window", async () => {
    gateway.getSchedule.mockResolvedValue([entry]);
    const { result } = renderHook(
      () =>
        useSchedule(
          "backend-guild",
          "2026-06-01T00:00:00Z",
          "2026-06-30T23:59:59Z",
        ),
      { wrapper },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(gateway.getSchedule.mock.calls[0]).toEqual([
      "backend-guild",
      "2026-06-01T00:00:00Z",
      "2026-06-30T23:59:59Z",
    ]);
  });

  it("keeps the previous window's data visible while a wider window is loading", async () => {
    gateway.getSchedule.mockResolvedValue([entry]);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    function localWrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      );
    }
    const { result, rerender } = renderHook(
      ({ toIso }: { toIso: string }) =>
        useSchedule("backend-guild", "2026-06-01T00:00:00Z", toIso),
      {
        wrapper: localWrapper,
        initialProps: { toIso: "2026-06-30T23:59:59Z" },
      },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    let resolveNextFetch: (entries: ScheduleEntry[]) => void = () => {};
    gateway.getSchedule.mockReturnValue(
      new Promise((resolve) => {
        resolveNextFetch = resolve;
      }),
    );
    rerender({ toIso: "2026-09-30T23:59:59Z" });

    expect(result.current.data).toHaveLength(1);
    expect(result.current.isPlaceholderData).toBe(true);

    resolveNextFetch([entry, entry]);
    await waitFor(() => expect(result.current.isPlaceholderData).toBe(false));
    expect(result.current.data).toHaveLength(2);
  });
});
