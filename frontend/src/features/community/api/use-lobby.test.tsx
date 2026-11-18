import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import {
  useLobby,
  useLobbySearch,
  useLobbyShelf,
} from "@/features/community/api/use-lobby";
import { useJoin } from "@/features/community/api/use-membership";
import type { Lobby, LobbyItem } from "@/features/community/domain/lobby";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    lobby: vi.fn(),
    lobbyShelf: vi.fn(),
    lobbySearch: vi.fn(),
    join: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function item(id: number): LobbyItem {
  return {
    id,
    slug: `community-${id}`,
    name: `Community ${id}`,
    kindId: 1,
    kind: "Guild",
    description: "A guild",
    status: "ACTIVE",
    memberCount: 3,
    mentoringEnabled: false,
    createdAt: "2026-05-31T10:00:00Z",
    updatedAt: "2026-05-31T10:00:00Z",
    role: null,
    nextSession: null,
    nextSessionThisWeek: false,
    latestSession: null,
    joinedLast30Days: 0,
    faces: [],
    stats: { sessionsHeld: 0, artifactsShared: 0 },
    lately: { artifactTitle: null, topicTitle: null },
  };
}

const lobby: Lobby = {
  billboard: [item(1)],
  shelves: [{ key: "GROWING", items: [item(1)], hasMore: true }],
};

function page(content: LobbyItem[], number: number, totalPages: number) {
  return { content, number, size: 8, totalElements: 30, totalPages };
}

function testClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
  });
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

describe("useLobby", () => {
  it("reads the lobby", async () => {
    // given
    gateway.lobby.mockResolvedValue(lobby);

    // when
    const { result } = renderHook(() => useLobby(), {
      wrapper: clientWrapper(testClient()),
    });

    // then
    await waitFor(() => expect(result.current.data).toEqual(lobby));
  });
});

describe("useLobbyShelf", () => {
  it("seeds page 0 from the lobby without a request", () => {
    // given
    const client = testClient();
    client.setQueryData(communityKeys.lobby(), lobby);

    // when
    const { result } = renderHook(
      () => useLobbyShelf("GROWING", [item(1)], true),
      { wrapper: clientWrapper(client) },
    );

    // then
    expect(result.current.data?.pages).toEqual([
      { items: [item(1)], hasMore: true },
    ]);
    expect(result.current.hasNextPage).toBe(true);
    expect(gateway.lobbyShelf).not.toHaveBeenCalled();
  });

  it("fetches page 1 at the shelf's page size on fetchNextPage", async () => {
    // given
    gateway.lobbyShelf.mockResolvedValue(page([item(2)], 1, 2));
    const client = testClient();
    client.setQueryData(communityKeys.lobby(), lobby);
    const { result } = renderHook(
      () => useLobbyShelf("GROWING", [item(1)], true),
      { wrapper: clientWrapper(client) },
    );
    expect(result.current.data?.pages).toHaveLength(1);

    // when
    await act(() => result.current.fetchNextPage());

    // then
    expect(gateway.lobbyShelf).toHaveBeenCalledExactlyOnceWith("GROWING", 1, 8);
    await waitFor(() =>
      expect(result.current.data?.pages.map((p) => p.items)).toEqual([
        [item(1)],
        [item(2)],
      ]),
    );
    expect(result.current.hasNextPage).toBe(false);
  });

  it("pages a grid shelf by 20", async () => {
    // given
    gateway.lobbyShelf.mockResolvedValue(page([item(2)], 1, 3));
    const { result } = renderHook(
      () => useLobbyShelf("OTHERS", [item(1)], true),
      { wrapper: clientWrapper(testClient()) },
    );
    expect(result.current.data?.pages).toHaveLength(1);

    // when
    await act(() => result.current.fetchNextPage());

    // then
    expect(gateway.lobbyShelf).toHaveBeenCalledExactlyOnceWith("OTHERS", 1, 20);
    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2));
    expect(result.current.hasNextPage).toBe(true);
  });

  it("has no next page when the lobby says the shelf is complete", () => {
    // when
    const { result } = renderHook(
      () => useLobbyShelf("JOINED", [item(1)], false),
      { wrapper: clientWrapper(testClient()) },
    );

    // then
    expect(result.current.hasNextPage).toBe(false);
  });

  it("never reads page 0 itself, even when the lobby it came from is stale", async () => {
    // given
    const client = testClient();
    client.setQueryData(communityKeys.lobby(), lobby, {
      updatedAt: Date.now() - 60_000,
    });
    await client.invalidateQueries({ queryKey: communityKeys.lists() });

    // when
    const { result } = renderHook(
      () => useLobbyShelf("GROWING", [item(1)], true),
      { wrapper: clientWrapper(client) },
    );

    // then
    expect(result.current.data?.pages).toEqual([
      { items: [item(1)], hasMore: true },
    ]);
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(gateway.lobbyShelf).not.toHaveBeenCalled();
  });

  it("starts afresh from the first page of a newer lobby read", () => {
    // given
    const client = testClient();
    client.setQueryData(communityKeys.lobby(), lobby, {
      updatedAt: Date.now() - 60_000,
    });
    const { result, rerender } = renderHook(
      ({ first }) => useLobbyShelf("GROWING", first, false),
      { wrapper: clientWrapper(client), initialProps: { first: [item(1)] } },
    );
    expect(result.current.data?.pages).toHaveLength(1);

    // when
    client.setQueryData(communityKeys.lobby(), lobby);
    rerender({ first: [item(4)] });

    // then
    expect(result.current.data?.pages).toEqual([
      { items: [item(4)], hasMore: false },
    ]);
  });
});

describe("useLobbySearch", () => {
  it("reads nothing for blank text", () => {
    // when
    const { result } = renderHook(() => useLobbySearch("   "), {
      wrapper: clientWrapper(testClient()),
    });

    // then
    expect(result.current.fetchStatus).toBe("idle");
    expect(gateway.lobbySearch).not.toHaveBeenCalled();
  });

  it("searches the trimmed text and pages by 20", async () => {
    // given
    gateway.lobbySearch
      .mockResolvedValueOnce(page([item(1)], 0, 2))
      .mockResolvedValueOnce(page([item(2)], 1, 2));
    const { result } = renderHook(() => useLobbySearch(" guild "), {
      wrapper: clientWrapper(testClient()),
    });
    await waitFor(() => expect(result.current.hasNextPage).toBe(true));

    // when
    await act(() => result.current.fetchNextPage());

    // then
    expect(gateway.lobbySearch.mock.calls).toEqual([
      ["guild", 0, 20],
      ["guild", 1, 20],
    ]);
    await waitFor(() => expect(result.current.hasNextPage).toBe(false));
  });
});

describe("lobby freshness after a join", () => {
  it("marks the lobby, its shelves and its searches stale", async () => {
    // given
    gateway.join.mockResolvedValue({
      id: 9,
      communityId: 1,
      userId: "alice",
      role: "MEMBER",
      joinedAt: "2026-05-31T10:00:00Z",
      updatedAt: "2026-05-31T10:00:00Z",
    });
    const client = testClient();
    const keys = [
      communityKeys.lobby(),
      communityKeys.lobbyShelf("GROWING"),
      communityKeys.lobbySearch("guild"),
    ];
    for (const key of keys) {
      client.setQueryData(key, {});
    }
    const { result } = renderHook(() => useJoin("community-1"), {
      wrapper: clientWrapper(client),
    });

    // when
    result.current.mutate();

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(keys.map((key) => client.getQueryState(key)?.isInvalidated)).toEqual(
      [true, true, true],
    );
  });
});
