import { useMemo } from "react";

import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { type Ceiling, knownCodes } from "@/features/admin/domain/ceiling";
import { usePermissionCatalog } from "@/features/admin/ui/use-permission-catalog";

/** The caller's privilege ceiling in this edition: what the account holds, measured only over
 *  the codes the edition knows. Read from the edition's whole catalog, never the kind-scoped
 *  one, since the server knows an enterprise-only code inside a hub too. */
export function useCeiling(): Ceiling {
  const held = useHeldPermissions();
  const catalog = usePermissionCatalog();
  const known = useMemo(() => knownCodes(catalog), [catalog]);
  return useMemo(() => ({ held, known }), [held, known]);
}
