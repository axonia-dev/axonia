import {
  type InfiniteData,
  keepPreviousData,
  type QueryClient,
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useMemo, useSyncExternalStore } from "react";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import {
  GRID_PAGE_SIZE,
  type Lobby,
  type LobbyItem,
  type LobbyShelfKey,
  shelfPageSize,
} from "@/features/community/domain/lobby";
import type { Page } from "@/lib/page";

const gateway = httpCommunityGateway;

export interface LobbyShelfPage {
  items: LobbyItem[];
  hasMore: boolean;
}

function hasNextPage(page: Page<LobbyItem>): boolean {
  return page.number + 1 < page.totalPages;
}

export function useLobby() {
  return useQuery({
    queryKey: communityKeys.lobby(),
    queryFn: () => gateway.lobby(),
  });
}

/**
 * A shelf's first page arrives with the lobby, which owns it: the shelf is keyed by the lobby read
 * it was seeded from and never reads page 0 itself. A fresh lobby starts a fresh shelf from its own
 * first page, so the two can never disagree; later pages are read only when asked for.
 */
export function useLobbyShelf(
  key: LobbyShelfKey,
  firstPage: LobbyItem[],
  hasMore: boolean,
) {
  const queryClient = useQueryClient();
  return useInfiniteQuery({
    queryKey: shelfQueryKey(queryClient, key),
    queryFn: async ({ pageParam }): Promise<LobbyShelfPage> => {
      const page = await gateway.lobbyShelf(key, pageParam, shelfPageSize(key));
      return { items: page.content, hasMore: hasNextPage(page) };
    },
    initialPageParam: 0,
    getNextPageParam: (last, pages) =>
      last.hasMore ? pages.length : undefined,
    initialData: { pages: [{ items: firstPage, hasMore }], pageParams: [0] },
    staleTime: "static",
  });
}

function shelfQueryKey(queryClient: QueryClient, key: LobbyShelfKey) {
  const seededAt =
    queryClient.getQueryState(communityKeys.lobby())?.dataUpdatedAt ?? 0;
  return [...communityKeys.lobbyShelf(key), seededAt];
}

function shelfSlugs(queryClient: QueryClient, lobby: Lobby | undefined) {
  return (lobby?.shelves ?? []).map((shelf) => {
    const read = queryClient.getQueryData<InfiniteData<LobbyShelfPage>>(
      shelfQueryKey(queryClient, shelf.key),
    );
    const pages = read?.pages ?? [{ items: shelf.items }];
    return [
      shelf.key,
      pages.flatMap((page) => page.items.map((item) => item.slug)),
    ] as const;
  });
}

/**
 * Every shelf's communities in the order they render, every loaded page included, read straight
 * from the cache so a lobby mounting from cache knows them in its very first render.
 */
export function useShelfSlugs(
  lobby: Lobby | undefined,
): ReadonlyMap<LobbyShelfKey, readonly string[]> {
  const queryClient = useQueryClient();
  const snapshot = useSyncExternalStore(
    (changed) => queryClient.getQueryCache().subscribe(changed),
    () => JSON.stringify(shelfSlugs(queryClient, lobby)),
  );
  return useMemo(
    () => new Map(JSON.parse(snapshot) as [LobbyShelfKey, readonly string[]][]),
    [snapshot],
  );
}

export function useLobbySearch(q: string) {
  const text = q.trim();
  return useInfiniteQuery({
    queryKey: communityKeys.lobbySearch(text),
    queryFn: ({ pageParam }) =>
      gateway.lobbySearch(text, pageParam, GRID_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (last) =>
      hasNextPage(last) ? last.number + 1 : undefined,
    enabled: text !== "",
    placeholderData: keepPreviousData,
  });
}
