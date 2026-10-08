import { useIsOrgless } from "@/auth/use-is-orgless";
import { useAccessMe } from "@/features/admin/api/use-access-me";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

/** Whether one act is allowed, over the same codes `useHeldPermissions` answers the account's
 *  whole reach with. Both read the one `/access/me` query, so asking here costs no request.
 *  A person with no organization holds nothing, known at once. `isError` tells a failed read
 *  apart from a refusal, since `can()` is false for everything in both. It is set only when there
 *  is nothing to answer from, and holds through a retry, so a gate never reads the retry as
 *  loading and remounts the page that asked. `retry` asks again through this same observer, so
 *  a failure screen never mounts a second one of its own. */
export function usePermissions(): Readonly<{
  can: (permission: string) => boolean;
  isLoading: boolean;
  isError: boolean;
  retry: RetryableRead;
}> {
  const orgless = useIsOrgless();
  const access = useAccessMe();
  const held = useHeldPermissions();
  const failed = failedToLoad(access);
  return {
    can: (permission: string) => held.has(permission),
    isLoading: access.isPending && !orgless && !failed,
    isError: failed,
    retry: access,
  };
}
