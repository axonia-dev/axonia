import { Copy, MoreHorizontal, Pencil, Trash2 } from "lucide-react";

import { MenuButton, MenuItem, MenuNote } from "@/components/ui/Menu";
import type { Role } from "@/features/admin/domain/admin-user";
import { isWithinReach } from "@/features/admin/domain/ceiling";
import { isEditable } from "@/features/admin/domain/role-editability";
import { REACHES_BEYOND } from "@/features/admin/ui/ceiling-copy";
import { useCeiling } from "@/features/admin/ui/use-ceiling";

export function RoleActionsMenu({
  role,
  canWrite,
  onRename,
  onDuplicate,
  onDelete,
}: Readonly<{
  role: Role;
  canWrite: boolean;
  onRename: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}>) {
  const ceiling = useCeiling();
  // Duplicate creates a role, so it needs `role:write` exactly as rename and delete do: a
  // reader is offered none of the three, and the trigger itself does not render. A system
  // role is immutable at the service layer, so a writer gets the one act that produces a new
  // role rather than changing this one.
  if (!canWrite) return null;
  const editable = isEditable(role, canWrite);
  // Delete and duplicate both turn into a server-side permission change (removing every
  // permission the role grants, or creating a role that grants them again), so both are
  // refused past the caller's ceiling exactly as RoleService guards them. Rename changes
  // neither the set of permissions a role grants nor who holds what, so it stays untouched.
  const outOfReach = !isWithinReach(role, ceiling);
  return (
    <MenuButton
      label="Role actions"
      trigger={<MoreHorizontal size={16} aria-hidden />}
      className="text-muted-foreground hover:text-foreground border-border bg-surface rounded-md border px-2.5 py-1.5"
      onAction={(key) => {
        if (key === "rename") onRename();
        if (key === "duplicate") onDuplicate();
        if (key === "delete") onDelete();
      }}
    >
      {editable && (
        <MenuItem id="rename">
          <Pencil className="h-3.5 w-3.5 shrink-0" />
          Rename
        </MenuItem>
      )}
      <MenuItem id="duplicate" isDisabled={outOfReach}>
        <Copy className="h-3.5 w-3.5 shrink-0" />
        Duplicate
      </MenuItem>
      {editable && (
        <MenuItem id="delete" tone="danger" isDisabled={outOfReach}>
          <Trash2 className="h-3.5 w-3.5 shrink-0" />
          Delete
        </MenuItem>
      )}
      {outOfReach && <MenuNote>{REACHES_BEYOND}</MenuNote>}
    </MenuButton>
  );
}
