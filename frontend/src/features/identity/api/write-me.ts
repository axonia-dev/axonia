import type { QueryClient } from "@tanstack/react-query";

import { identityKeys } from "@/features/identity/api/query-keys";
import type { User } from "@/features/identity/domain/user";

/** Writes a refreshed profile into the `me` cache. Every mutation on the caller's own profile
 *  answers with the whole profile, so the cache is set from the response rather than invalidated:
 *  the avatar must not blink through a refetch. */
export function writeMe(queryClient: QueryClient) {
  return (user: User) => queryClient.setQueryData<User>(identityKeys.me, user);
}
