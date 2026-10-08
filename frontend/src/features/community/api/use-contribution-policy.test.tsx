import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import {
  useContributionPolicy,
  useContributionPolicyQuery,
  useUpdateContributionPolicy,
} from "@/features/community/api/use-contribution-policy";
import { curated } from "@/features/community/domain/contribution-policy";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    getContributionPolicy: vi.fn(),
    updateContributionPolicy: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const openPolicy = {
  canStartSeries: true,
  canEditStructure: true,
  canQueueTopics: false,
  canManageSchedule: false,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useContributionPolicy", () => {
  it("returns curated defaults while loading", () => {
    // given a request that never settles
    gateway.getContributionPolicy.mockReturnValue(new Promise(() => {}));

    // when
    const { result } = renderHook(() => useContributionPolicy("g"), {
      wrapper,
    });

    // then
    expect(result.current).toEqual(curated());
  });

  it("returns the fetched policy", async () => {
    // given
    gateway.getContributionPolicy.mockResolvedValue(openPolicy);

    // when
    const { result } = renderHook(() => useContributionPolicy("g"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current).toEqual(openPolicy));
  });

  it("returns curated defaults on error", async () => {
    // given
    gateway.getContributionPolicy.mockRejectedValue(new Error("boom"));

    // when
    const { result } = renderHook(
      () => ({
        policy: useContributionPolicy("g"),
        query: useContributionPolicyQuery("g"),
      }),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.query.isError).toBe(true));
    expect(result.current.policy).toEqual(curated());
  });
});

describe("useUpdateContributionPolicy", () => {
  it("saves the policy through the gateway", async () => {
    // given
    gateway.updateContributionPolicy.mockResolvedValue(openPolicy);
    const { result } = renderHook(() => useUpdateContributionPolicy("g"), {
      wrapper,
    });

    // when
    result.current.mutate(openPolicy);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.updateContributionPolicy.mock.calls[0]).toEqual([
      "g",
      openPolicy,
    ]);
  });
});
