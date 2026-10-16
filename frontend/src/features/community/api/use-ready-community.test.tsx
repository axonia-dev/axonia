import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import { useReadyCommunity } from "@/features/community/api/use-ready-community";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: { getBySlug: vi.fn() },
}));

const gateway = vi.mocked(httpCommunityGateway);

function setup() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const { result } = renderHook(() => useReadyCommunity(), { wrapper });
  return { client, community: result.current };
}

beforeEach(() => vi.clearAllMocks());

describe("useReadyCommunity", () => {
  it("resolves once the community is cached", async () => {
    // given
    gateway.getBySlug.mockResolvedValue({ slug: "guild" } as never);
    const { client, community } = setup();

    // when
    await act(() => community.ready("guild"));

    // then
    expect(client.getQueryData(communityKeys.detail("guild"))).toEqual({
      slug: "guild",
    });
  });

  it("resolves at once on a seeded community, however stale", async () => {
    // given
    const { client, community } = setup();
    client.setQueryData(
      communityKeys.detail("guild"),
      { slug: "guild" } as never,
      { updatedAt: 0 },
    );

    // when
    await act(() => community.ready("guild"));

    // then
    expect(gateway.getBySlug).not.toHaveBeenCalled();
  });

  it("asks the server once for two prefetches in a row", async () => {
    // given
    gateway.getBySlug.mockResolvedValue({ slug: "guild" } as never);
    const { client, community } = setup();

    // when
    act(() => {
      community.prefetch("guild");
      community.prefetch("guild");
    });
    await vi.waitFor(() =>
      expect(client.getQueryData(communityKeys.detail("guild"))).toBeDefined(),
    );

    // then
    expect(gateway.getBySlug).toHaveBeenCalledOnce();
  });
});
