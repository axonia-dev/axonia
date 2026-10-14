import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import { useJoin, useLeave } from "@/features/community/api/use-membership";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: { join: vi.fn(), leave: vi.fn() },
}));

const gateway = vi.mocked(httpCommunityGateway);

const membership = {
  id: 9,
  communityId: 1,
  userId: "alice",
  role: "MEMBER" as const,
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function testClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function clientWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("community lists", () => {
  it("refreshes every community list after a join", async () => {
    // given
    gateway.join.mockResolvedValue(membership);
    const client = testClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useJoin("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: communityKeys.lists(),
    });
  });

  it("refreshes every community list after a leave", async () => {
    // given
    gateway.leave.mockResolvedValue(undefined);
    const client = testClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useLeave("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: communityKeys.lists(),
    });
  });
});
