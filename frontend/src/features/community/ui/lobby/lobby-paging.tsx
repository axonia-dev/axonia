import { useCallback } from "react";

import { Button } from "@/components/ui/Button";
import { useLobbyShelf } from "@/features/community/api/use-lobby";
import type { Lobby } from "@/features/community/domain/lobby";
import {
  type LobbyFaces,
  useLobbyFaces,
} from "@/features/community/ui/lobby/use-lobby-faces";

export type Shelf = Lobby["shelves"][number];

/** Where a paged list stands: whether more exists, whether it is arriving, whether it failed. */
export interface Paging {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  /** The last page asked for failed: nothing asks again until the person does. */
  failed: boolean;
  loadMore: () => void;
}

/** Whether a loader may ask for the next page on its own. */
export function mayLoadMore(paging: Paging): boolean {
  return paging.hasNextPage && !paging.isFetchingNextPage && !paging.failed;
}

interface InfiniteRead {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  isFetchNextPageError: boolean;
  fetchNextPage: () => Promise<unknown>;
}

export function usePaging(query: InfiniteRead): Paging {
  const { fetchNextPage } = query;
  const loadMore = useCallback(() => void fetchNextPage(), [fetchNextPage]);
  return {
    hasNextPage: query.hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    failed: query.isFetchNextPageError,
    loadMore,
  };
}

/**
 * A shelf's cards and the faces they wear. The lobby already resolved the faces of the first
 * page; any card beyond it is resolved here, in one read for all of them.
 */
export function useShelfPages(shelf: Shelf, lobbyFaces: LobbyFaces) {
  const query = useLobbyShelf(shelf.key, shelf.items, shelf.hasMore);
  const items = query.data.pages.flatMap((page) => page.items);
  const seeded = new Set(shelf.items.map((item) => item.id));
  const faces = useLobbyFaces(
    items.filter((item) => !seeded.has(item.id)),
    lobbyFaces,
  );
  return { items, faces, paging: usePaging(query) };
}

/** Said where the next page should have appeared, with the one way to ask again. */
export function LoadMoreFailed({ paging }: Readonly<{ paging: Paging }>) {
  if (!paging.failed) return null;
  return (
    <div className="flex items-center gap-3">
      <p role="alert" className="text-danger text-sm">
        Could not load more communities.
      </p>
      <Button
        variant="outline"
        size="sm"
        isPending={paging.isFetchingNextPage}
        onPress={paging.loadMore}
      >
        Try again
      </Button>
    </div>
  );
}
