import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpRecognitionGateway } from "@/features/recognition/adapters/http-recognition-gateway";
import {
  useLove,
  useLoveSummary,
  useMyContributions,
  useUnlove,
} from "@/features/recognition/api/use-recognition";

vi.mock("@/features/recognition/adapters/http-recognition-gateway", () => ({
  httpRecognitionGateway: {
    getSummary: vi.fn(),
    love: vi.fn(),
    unlove: vi.fn(),
    listMyContributions: vi.fn(),
  },
}));

const gateway = vi.mocked(httpRecognitionGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const key = {
  contributionType: "SLOT_PRESENTED" as const,
  contributionId: 9,
};

beforeEach(() => vi.clearAllMocks());

describe("useLoveSummary", () => {
  it("fetches the summary for a contribution", async () => {
    // given
    gateway.getSummary.mockResolvedValue({ count: 4, viewerHasLoved: false });

    // when
    const { result } = renderHook(() => useLoveSummary(key), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.count).toBe(4);
  });
});

describe("useLove", () => {
  it("loves through the gateway", async () => {
    // given
    gateway.love.mockResolvedValue({} as never);
    const { result } = renderHook(() => useLove(key), { wrapper });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(gateway.love.mock.calls).toEqual([[key]]));
  });
});

describe("useUnlove", () => {
  it("unloves through the gateway", async () => {
    // given
    gateway.unlove.mockResolvedValue();
    const { result } = renderHook(() => useUnlove(key), { wrapper });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(gateway.unlove.mock.calls[0]).toEqual([key]));
  });
});

describe("useMyContributions", () => {
  it("lists the caller's contributions", async () => {
    // given
    gateway.listMyContributions.mockResolvedValue([]);
    const { result } = renderHook(() => useMyContributions(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
