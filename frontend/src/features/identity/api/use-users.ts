import {
  type QueryClient,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { identityKeys } from "@/features/identity/api/query-keys";
import type { User } from "@/features/identity/domain/user";

const gateway = httpIdentityGateway;

function byId(users: User[]): Record<string, User> {
  return Object.fromEntries(users.map((user) => [user.id, user]));
}

interface Copy {
  user: User;
  updatedAt: number;
}

/** Each wanted person's most recently resolved copy across the batches in the cache. */
function freshestCopies(
  client: QueryClient,
  wanted: Set<string>,
): Map<string, Copy> {
  const freshest = new Map<string, Copy>();
  for (const { state } of client
    .getQueryCache()
    .findAll({ queryKey: identityKeys.anyUsers })) {
    const users = (state.data ?? []) as User[];
    for (const user of users.filter(({ id }) => wanted.has(id))) {
      const known = freshest.get(user.id);
      if (known === undefined || state.dataUpdatedAt > known.updatedAt) {
        freshest.set(user.id, { user, updatedAt: state.dataUpdatedAt });
      }
    }
  }
  return freshest;
}

interface Resolved {
  users: User[];
  updatedAt: number;
}

/** Everybody in `ids` from batches already in the cache, or nothing unless all of them are there.
 *  A dialog naming one person from a list on screen then shows the name the list shows at once,
 *  rather than a placeholder while that person is asked for again. The seed is as old as its
 *  oldest copy, so a stale one is still refetched. */
function alreadyResolved(
  client: QueryClient,
  ids: string[],
): Resolved | undefined {
  const wanted = new Set(ids);
  if (wanted.size === 0) return undefined;
  const copies = [...freshestCopies(client, wanted).values()];
  if (copies.length < wanted.size) return undefined;
  return {
    users: copies.map(({ user }) => user),
    updatedAt: Math.min(...copies.map(({ updatedAt }) => updatedAt)),
  };
}

/** The query reads a seed's age right after the seed itself, so one look through the cache serves both. */
function seededFromCache(client: QueryClient, ids: string[]) {
  let seed: Resolved | undefined;
  return {
    initialData: () => {
      seed = alreadyResolved(client, ids);
      return seed?.users;
    },
    initialDataUpdatedAt: () => seed?.updatedAt,
  };
}

/**
 * Batch-resolves user ids for display, keyed by id. The batch is total: an id that resolves to
 * no row still comes back, under `memberStatus: "ERASED"` and the backend's neutral label, rather
 * than being omitted.
 */
export function useUsers(ids: string[]) {
  const client = useQueryClient();
  return useQuery({
    queryKey: identityKeys.users(ids),
    queryFn: () => gateway.resolve(ids),
    ...seededFromCache(client, ids),
    enabled: ids.length > 0,
    select: byId,
  });
}
