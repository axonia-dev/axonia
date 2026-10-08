import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { FOLLOWS_COMMUNITY_UPDATES } from "@/features/community/api/query-keys";
import {
  useArchiveCommunity,
  useCommunities,
  useCommunity,
  useCreateCommunity,
  useUpdateCommunity,
} from "@/features/community/api/use-communities";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    list: vi.fn(),
    getBySlug: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    archive: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE" as const,
  memberCount: 1,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useCommunities", () => {
  it("returns the directory page", async () => {
    // given
    gateway.list.mockResolvedValue({
      content: [community],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    // when
    const { result } = renderHook(
      () => useCommunities({ page: 0, size: 20, q: "" }),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(1);
  });
});

describe("useCommunity", () => {
  it("returns one community", async () => {
    // given
    gateway.getBySlug.mockResolvedValue(community);

    // when
    const { result } = renderHook(() => useCommunity("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.slug).toBe("backend-guild");
  });

  it("reads nothing without a slug", () => {
    // when
    const { result } = renderHook(() => useCommunity(undefined), { wrapper });

    // then
    expect(result.current.isPending).toBe(true);
    expect(gateway.getBySlug).not.toHaveBeenCalled();
  });
});

describe("useCreateCommunity", () => {
  it("creates a community", async () => {
    // given
    gateway.create.mockResolvedValue(community);
    const { result } = renderHook(() => useCreateCommunity(), { wrapper });

    // when
    result.current.mutate({
      name: "Backend Guild",
      kindId: 1,
      description: "A guild",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.create.mock.calls).toHaveLength(1);
  });
});

describe("useUpdateCommunity", () => {
  it("updates a community and invalidates its caches", async () => {
    // given
    gateway.update.mockResolvedValue(community);
    const { result } = renderHook(() => useUpdateCommunity("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({
      name: "Renamed",
      kindId: 1,
      description: "x",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.update.mock.calls[0]?.[0]).toBe("backend-guild");
  });

  it("refreshes the organization card caches (switcher, current org)", async () => {
    // given - in a hub, the community's name IS the organization's public name
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    gateway.update.mockResolvedValue(community);
    const { result } = renderHook(() => useUpdateCommunity("backend-guild"), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });

    // when
    result.current.mutate({ name: "Renamed", kindId: 1, description: "x" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const keys = invalidate.mock.calls.map((call) => call[0]?.queryKey);
    expect(keys).toContainEqual(["admin", "my-organizations"]);
    expect(keys).toContainEqual(["admin", "current-organization"]);
  });

  it("refreshes every read that declares it follows community updates, and no other", async () => {
    // given
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const following = vi.fn<() => Promise<string>>().mockResolvedValue("card");
    const unrelated = vi.fn<() => Promise<string>>().mockResolvedValue("other");
    gateway.update.mockResolvedValue(community);
    const { result } = renderHook(
      () => {
        useQuery({
          queryKey: ["probe", "following"],
          queryFn: following,
          meta: FOLLOWS_COMMUNITY_UPDATES,
        });
        useQuery({ queryKey: ["probe", "unrelated"], queryFn: unrelated });
        return useUpdateCommunity("backend-guild");
      },
      {
        wrapper: ({ children }) => (
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        ),
      },
    );
    await waitFor(() => expect(following).toHaveBeenCalledOnce());
    await waitFor(() => expect(unrelated).toHaveBeenCalledOnce());

    // when
    result.current.mutate({ name: "Renamed", kindId: 1, description: "x" });

    // then
    await waitFor(() => expect(following).toHaveBeenCalledTimes(2));
    expect(unrelated).toHaveBeenCalledOnce();
  });
});

describe("useArchiveCommunity", () => {
  it("archives a community and invalidates its caches", async () => {
    // given
    gateway.archive.mockResolvedValue({ ...community, status: "ARCHIVED" });
    const { result } = renderHook(() => useArchiveCommunity("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.archive.mock.calls[0]).toEqual(["backend-guild"]);
  });
});
