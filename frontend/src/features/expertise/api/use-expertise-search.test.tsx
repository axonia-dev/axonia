import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    search: vi.fn(),
  },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

const practitioner = {
  userId: "alice",
  displayName: "Alice",
  avatarUrl: null,
  orgUnit: null,
  tier: "PRACTITIONER" as const,
  evidence: {
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
  },
  openToQuestions: false,
  declaredAt: null,
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useExpertiseSearch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns the list of practitioners for the given params", async () => {
    // given
    gateway.search.mockResolvedValue([practitioner]);

    // when
    const { result } = renderHook(
      () =>
        useExpertiseSearch("backend-guild", {
          term: "hexagon",
          demonstratedOnly: false,
        }),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.userId).toBe("alice");
    expect(gateway.search.mock.calls[0]).toEqual([
      "backend-guild",
      { term: "hexagon", demonstratedOnly: false },
    ]);
  });

  it("does not call the gateway when the term is empty", () => {
    // given
    gateway.search.mockResolvedValue([]);

    // when
    const { result } = renderHook(
      () =>
        useExpertiseSearch("backend-guild", {
          term: "",
          demonstratedOnly: false,
        }),
      { wrapper },
    );

    // then - the query stays idle rather than firing a term-less request
    expect(result.current.fetchStatus).toBe("idle");
    expect(gateway.search.mock.calls).toHaveLength(0);
  });

  it("returns an empty list when gateway resolves nothing", async () => {
    // given
    gateway.search.mockResolvedValue([]);

    // when
    const { result } = renderHook(
      () =>
        useExpertiseSearch("backend-guild", {
          term: "unknown",
          demonstratedOnly: false,
        }),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});
