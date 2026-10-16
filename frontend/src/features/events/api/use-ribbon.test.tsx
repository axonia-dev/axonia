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

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useRibbon", () => {
  it("fetches the ribbon for the given window", async () => {
    gateway.getRibbon.mockResolvedValue({ firstSessionAt: null, ticks: [] });
    const { result } = renderHook(
      () =>
        useRibbon(
          "backend-guild",
          "2025-10-16T00:00:00Z",
          "2027-01-14T00:00:00Z",
        ),
      { wrapper },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.getRibbon.mock.calls[0]).toEqual([
      "backend-guild",
      "2025-10-16T00:00:00Z",
      "2027-01-14T00:00:00Z",
    ]);
  });
});
