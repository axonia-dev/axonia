import { Lock } from "lucide-react";

import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";
import { lockedCountLine } from "@/features/admin/ui/ceiling-copy";

/** The one-line disclosure of the ceiling: how many applicable permissions the caller cannot
 *  change. A fact about the account, not the open role, so it does not move as roles are
 *  opened. Renders nothing once the caller holds the whole applicable catalog, the Owner's
 *  own case. */
export function CeilingLine({
  catalog,
  heldPermissions,
}: Readonly<{
  catalog: PermissionCatalog;
  heldPermissions: ReadonlySet<string>;
}>) {
  const locked = catalog.entries.filter(
    (p) => !heldPermissions.has(p.code),
  ).length;
  if (locked === 0) return null;
  return (
    <p className="border-border-strong bg-surface-sunken text-muted-foreground flex items-start gap-2 rounded-md border px-3 py-2 text-xs">
      <Lock aria-hidden className="mt-px size-3.5 shrink-0" />
      <span>{lockedCountLine(locked, catalog.entries.length)}</span>
    </p>
  );
}
