import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useRibbon } from "@/features/events/api/use-ribbon";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { getRibbon: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

function wrapperOf(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

const newClient = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false } } });

beforeEach(() => vi.clearAllMocks());

describe("useRibbon", () => {
  it("asks for each year once and merges their ticks in order", async () => {
    // given
    gateway.getRibbon.mockImplementation((_slug, from) =>
      Promise.resolve({
        firstSessionAt: "2025-03-01T18:00:00Z",
        ticks: [{ at: from.replace("01-01", "03-01"), kind: "MEMORY" }],
      }),
    );
    // when
    const { result } = renderHook(
      () => useRibbon("backend-guild", [2026, 2025]),
      {
        wrapper: wrapperOf(newClient()),
      },
    );
    // then
    await waitFor(() => expect(result.current.ticks).toHaveLength(2));
    expect(gateway.getRibbon.mock.calls).toEqual([
      ["backend-guild", "2026-01-01T00:00:00.000Z", "2027-01-01T00:00:00.000Z"],
      ["backend-guild", "2025-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z"],
    ]);
    expect(result.current.ticks.map((t) => t.at)).toEqual([
      "2025-03-01T00:00:00.000Z",
      "2026-03-01T00:00:00.000Z",
    ]);
    expect(result.current.firstSessionAt).toBe("2025-03-01T18:00:00Z");
  });

  it("knows no first session until a year answers", () => {
    // given
    gateway.getRibbon.mockReturnValue(new Promise(() => {}));
    // when
    const { result } = renderHook(() => useRibbon("backend-guild", [2026]), {
      wrapper: wrapperOf(newClient()),
    });
    // then
    expect(result.current).toEqual({ ticks: [], firstSessionAt: undefined });
  });

  it("reuses a loaded year when the window rolls on", async () => {
    // given
    gateway.getRibbon.mockResolvedValue({ firstSessionAt: null, ticks: [] });
    const client = newClient();
    const { result, rerender } = renderHook(
      ({ years }) => useRibbon("backend-guild", years),
      { wrapper: wrapperOf(client), initialProps: { years: [2026] } },
    );
    await waitFor(() => expect(result.current.firstSessionAt).toBeNull());
    // when
    rerender({ years: [2026, 2027] });
    // then
    await waitFor(() => expect(gateway.getRibbon).toHaveBeenCalledTimes(2));
    expect(gateway.getRibbon.mock.calls[1]?.[1]).toBe(
      "2027-01-01T00:00:00.000Z",
    );
  });
});
