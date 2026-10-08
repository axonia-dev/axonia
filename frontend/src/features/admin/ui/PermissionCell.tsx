import { Check, Minus } from "lucide-react";

import { cn } from "@/lib/cn";

interface PermissionCellProps {
  granted: boolean;
  /** The permission's human label. */
  label: string;
  /** The role the mark belongs to, which is never the role being edited. */
  roleName: string;
}

/**
 * A read-only grant mark for the comparison column. Violet rather than primary, and never
 * interactive: it states what the pinned role holds, and nothing here can change that role.
 */
export function PermissionCell({
  granted,
  label,
  roleName,
}: Readonly<PermissionCellProps>) {
  const Icon = granted ? Check : Minus;
  return (
    <span
      role="img"
      aria-label={`${roleName} ${granted ? "grants" : "does not grant"} ${label}`}
      className={cn(
        "grid size-5 place-items-center rounded-md border",
        granted
          ? "border-secondary/40 bg-secondary/20 text-secondary"
          : "border-border-strong text-border-strong",
      )}
    >
      <Icon aria-hidden className="size-3" />
    </span>
  );
}
