import { useMemo } from "react";

import { useAccessMe } from "@/features/admin/api/use-access-me";

/** The caller's own permission codes, which are exactly the privilege ceiling every act that
 *  moves authority is measured against. Held apart from `usePermissions` because `can` answers
 *  whether one act is allowed, and this answers what the account's whole reach is. */
export function useHeldPermissions(): ReadonlySet<string> {
  const { data } = useAccessMe();
  return useMemo(() => new Set(data?.permissions ?? []), [data?.permissions]);
}
