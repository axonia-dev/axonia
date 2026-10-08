import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { adminKeys } from "@/features/admin/api/query-keys";
import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import {
  useChangeRole,
  useJoin,
  useLeave,
  useMembers,
  useMemberSearch,
} from "@/features/community/api/use-membership";
import type { Membership } from "@/features/community/domain/membership";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { learningKeys } from "@/features/learning/api/query-keys";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    listMembers: vi.fn(),
    searchMembers: vi.fn(),
    join: vi.fn(),
    leave: vi.fn(),
    changeRole: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function testClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function wrapper({ children }: { children: ReactNode }) {
  const client = testClient();
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

// A single-use QueryClient wrapper for tests that need to inspect or seed
// that exact client (spying on it, or reading/setting its cache) rather than
// the disposable one `wrapper` creates per render.
function clientWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

const membership = {
  id: 9,
  communityId: 1,
  userId: "alice",
  role: "MEMBER" as const,
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useMembers", () => {
  it("returns the member page", async () => {
    // given
    gateway.listMembers.mockResolvedValue({
      content: [membership],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
    });

    // when
    const { result } = renderHook(
      () => useMembers("backend-guild", { page: 0, size: 50 }),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content[0]?.userId).toBe("alice");
  });
});

describe("useMemberSearch", () => {
  const bob = { userId: "bob", displayName: "Bob Martin" };

  it("asks the gateway once there is something to search for", async () => {
    // given
    gateway.searchMembers.mockResolvedValue([bob]);

    // when
    const { result } = renderHook(
      () => useMemberSearch("backend-guild", "bo"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.searchMembers).toHaveBeenCalledWith("backend-guild", "bo");
  });

  it("stays idle for blank text", () => {
    // when
    renderHook(() => useMemberSearch("backend-guild", "   "), { wrapper });

    // then
    expect(gateway.searchMembers).not.toHaveBeenCalled();
  });

  it("drops the previous results when the text is cleared", async () => {
    // given
    gateway.searchMembers.mockResolvedValue([bob]);
    const client = testClient();
    const { result, rerender } = renderHook(
      ({ text }) => useMemberSearch("backend-guild", text),
      { wrapper: clientWrapper(client), initialProps: { text: "bo" } },
    );
    await waitFor(() => expect(result.current.data).toEqual([bob]));

    // when
    rerender({ text: "" });

    // then
    expect(result.current.data).toBeUndefined();
  });
});

describe("useJoin", () => {
  it("joins a community", async () => {
    // given
    gateway.join.mockResolvedValue(membership);
    const { result } = renderHook(() => useJoin("backend-guild"), { wrapper });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.join.mock.calls[0]).toEqual(["backend-guild"]);
  });

  it("invalidates the seeded my-membership and Learning page queries on success", async () => {
    // given - the hero flips from Join to the chip after joining only because
    // communityKeys.myMembership(slug) is a prefix-child of
    // communityKeys.detail(slug), which useJoin invalidates. Prove it
    // behaviourally instead of trusting the comment.
    gateway.join.mockResolvedValue(membership);
    const client = testClient();
    client.setQueryData(communityKeys.myMembership("backend-guild"), null);
    client.setQueryData(learningKeys.page("backend-guild"), { member: false });
    const { result } = renderHook(() => useJoin("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      client.getQueryState(communityKeys.myMembership("backend-guild"))
        ?.isInvalidated,
    ).toBe(true);
    expect(
      client.getQueryState(learningKeys.page("backend-guild"))?.isInvalidated,
    ).toBe(true);
  });
});

describe("useLeave", () => {
  it("leaves a community", async () => {
    // given
    gateway.leave.mockResolvedValue(undefined);
    const { result } = renderHook(() => useLeave("backend-guild"), { wrapper });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.leave.mock.calls[0]).toEqual(["backend-guild"]);
  });

  it("invalidates the community detail, my-organizations, own expertise and Learning queries on success", async () => {
    // given - leaving can cascade to the org, which my-organizations reflects
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
    for (const queryKey of [
      communityKeys.detail("backend-guild"),
      adminKeys.myOrganizations(),
      expertiseKeys.selfView(),
      learningKeys.community("backend-guild"),
    ]) {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey });
    }
  });

  it("invalidates the seeded my-membership query on success", async () => {
    // given - the hero flips from the chip back to Join after a leave only
    // because communityKeys.myMembership(slug) is a prefix-child of
    // communityKeys.detail(slug), which useLeave invalidates. Prove it
    // behaviourally instead of trusting the comment.
    gateway.leave.mockResolvedValue(undefined);
    const client = testClient();
    client.setQueryData(communityKeys.myMembership("backend-guild"), {
      ...membership,
    });
    const { result } = renderHook(() => useLeave("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      client.getQueryState(communityKeys.myMembership("backend-guild"))
        ?.isInvalidated,
    ).toBe(true);
  });
});

function seededClient() {
  const client = testClient();
  client.setQueryData(
    communityKeys.members("backend-guild", { page: 0, size: 100 }),
    {
      content: [membership],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
    },
  );
  return client;
}

describe("useChangeRole", () => {
  it("optimistically promotes the member in the cache before the server responds", async () => {
    // given a member already cached and a slow server
    const client = seededClient();
    let resolve: (value: Membership) => void = () => {};
    gateway.changeRole.mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    const { result } = renderHook(() => useChangeRole("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    // when the role is changed
    act(() => result.current.mutate({ userId: "alice", role: "ORGANIZER" }));

    // then the cache shows ORGANIZER immediately (optimistic)
    await waitFor(() => {
      const page = client.getQueryData<{ content: Array<{ role: string }> }>(
        communityKeys.members("backend-guild", { page: 0, size: 100 }),
      );
      expect(page?.content[0]?.role).toBe("ORGANIZER");
    });
    act(() => resolve({ ...membership, role: "ORGANIZER" }));
  });

  it("rolls the cache back when the server rejects the change", async () => {
    // given a member and a server that refuses
    const client = seededClient();
    gateway.changeRole.mockRejectedValue(new Error("nope"));
    const { result } = renderHook(() => useChangeRole("backend-guild"), {
      wrapper: clientWrapper(client),
    });

    // when the change fails
    act(() => result.current.mutate({ userId: "alice", role: "ORGANIZER" }));

    // then the cached role snaps back to MEMBER
    await waitFor(() => expect(result.current.isError).toBe(true));
    const page = client.getQueryData<{ content: Array<{ role: string }> }>(
      communityKeys.members("backend-guild", { page: 0, size: 100 }),
    );
    expect(page?.content[0]?.role).toBe("MEMBER");
  });
});
