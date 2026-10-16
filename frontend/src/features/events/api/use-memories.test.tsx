import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useMemories } from "@/features/events/api/use-memories";
import type { MemoryPage } from "@/features/events/domain/memory";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { getMemories: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const cursor = { at: "2026-09-01T18:00:00Z", id: 4 };
const first: MemoryPage = { items: [], next: cursor };
const last: MemoryPage = { items: [], next: null };

beforeEach(() => vi.clearAllMocks());

describe("useMemories", () => {
  it("starts without a cursor and pages from the previous page's next", async () => {
    gateway.getMemories
      .mockResolvedValueOnce(first)
      .mockResolvedValueOnce(last);
    const { result } = renderHook(() => useMemories("backend-guild"), {
      wrapper,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.getMemories.mock.calls[0]).toEqual([
      "backend-guild",
      null,
      20,
    ]);
    expect(result.current.hasNextPage).toBe(true);

    await result.current.fetchNextPage();

    await waitFor(() => expect(result.current.hasNextPage).toBe(false));
    expect(gateway.getMemories.mock.calls[1]).toEqual([
      "backend-guild",
      cursor,
      20,
    ]);
  });
});
