import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useArtifactSessions,
  useLibraryStats,
} from "@/features/knowledge/api/use-library-insights";

vi.mock("@/features/knowledge/adapters/http-knowledge-gateway", () => ({
  httpKnowledgeGateway: { listSessions: vi.fn(), stats: vi.fn() },
}));

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";

const gateway = vi.mocked(httpKnowledgeGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useArtifactSessions", () => {
  it("lists the sessions that have artifacts", async () => {
    // given
    gateway.listSessions.mockResolvedValue([{ sessionId: 9, count: 4 }]);

    // when
    const { result } = renderHook(() => useArtifactSessions("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([{ sessionId: 9, count: 4 }]);
    expect(gateway.listSessions.mock.calls[0]).toEqual(["backend-guild"]);
  });
});

describe("useLibraryStats", () => {
  it("returns the vitality figures for the community", async () => {
    // given
    gateway.stats.mockResolvedValue({
      total: 12,
      sessionCount: 3,
      capturedThisMonth: 2,
    });

    // when
    const { result } = renderHook(() => useLibraryStats("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.total).toBe(12);
    expect(gateway.stats.mock.calls[0]).toEqual(["backend-guild"]);
  });
});
