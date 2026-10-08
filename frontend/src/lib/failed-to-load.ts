import type { QueryObserverResult } from "@tanstack/react-query";

/** A read that failed and has nothing to show. Unlike `isError` it holds through a retry:
 *  TanStack resets a query with no data to pending on every fetch, so a gate reading `isError`
 *  drops back to loading, and a subtree it remounts refetches the failed query on mount, which
 *  fails it again, forever. */
export function failedToLoad(
  query: Pick<QueryObserverResult, "data" | "errorUpdateCount">,
): boolean {
  return query.data === undefined && query.errorUpdateCount > 0;
}

/** A read still waiting for its first answer: no data yet, and no failure. */
export function stillLoading(
  query: Pick<QueryObserverResult, "data" | "errorUpdateCount">,
): boolean {
  return query.data === undefined && !failedToLoad(query);
}

/** What asking a failed read again needs. */
export type RetryableRead = Readonly<{
  isFetching: boolean;
  refetch: () => Promise<unknown>;
}>;

type Read = Pick<
  QueryObserverResult<unknown>,
  "data" | "errorUpdateCount" | "isFetching" | "refetch"
>;

/** One retry for a screen that failed on several reads: it asks again only for those that
 *  failed, so a single "Try again" stands for all of them. */
export function retryOfFailed(reads: ReadonlyArray<Read>): RetryableRead {
  const failed = reads.filter(failedToLoad);
  return {
    isFetching: failed.some((read) => read.isFetching),
    refetch: () => Promise.all(failed.map((read) => read.refetch())),
  };
}
