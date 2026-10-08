import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  useEndSeries,
  useSeriesList,
  useStartSeries,
  useUpdateSeries,
} from "@/features/events/api/use-series";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    startSeries: vi.fn(),
    updateSeries: vi.fn(),
    endSeries: vi.fn(),
    listSeries: vi.fn(),
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

describe("useStartSeries", () => {
  it("starts a series", async () => {
    gateway.startSeries.mockResolvedValue({} as never);
    const { result } = renderHook(() => useStartSeries("backend-guild"), {
      wrapper,
    });
    result.current.mutate({
      title: "X",
      cadence: { count: 1, unit: "WEEK" },
      startsOn: "2026-06-15",
      timeZone: "Europe/Zurich",
      place: EMPTY_PLACE,
      defaultStructure: [],
    });
    await waitFor(() => expect(gateway.startSeries.mock.calls).toHaveLength(1));
  });
});

describe("useUpdateSeries", () => {
  it("updates a series", async () => {
    gateway.updateSeries.mockResolvedValue({} as never);
    const { result } = renderHook(() => useUpdateSeries("backend-guild"), {
      wrapper,
    });
    result.current.mutate({
      id: 1,
      input: {
        title: "X",
        cadence: { count: 1, unit: "WEEK" },
        startsOn: "2026-06-15",
        timeZone: "Europe/Zurich",
        place: EMPTY_PLACE,
        defaultStructure: [],
      },
    });
    await waitFor(() =>
      expect(gateway.updateSeries.mock.calls).toHaveLength(1),
    );
  });
});

describe("useEndSeries", () => {
  it("ends a series", async () => {
    gateway.endSeries.mockResolvedValue({} as never);
    const { result } = renderHook(() => useEndSeries("backend-guild"), {
      wrapper,
    });

    result.current.mutate(1);

    await waitFor(() => expect(gateway.endSeries.mock.calls).toHaveLength(1));
    expect(gateway.endSeries).toHaveBeenCalledWith(1);
  });
});

describe("useSeriesList", () => {
  it("lists series", async () => {
    gateway.listSeries.mockResolvedValue({
      content: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    } as never);
    const { result } = renderHook(() => useSeriesList("backend-guild"), {
      wrapper,
    });
    await waitFor(() => expect(gateway.listSeries.mock.calls).toHaveLength(1));
    expect(result.current).toBeDefined();
  });
});
