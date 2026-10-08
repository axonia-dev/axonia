import type { Role } from "@/features/admin/domain/admin-user";

/** Whether the open role may be changed at all: every write the pane offers is refused at the
 *  service layer for a system role, and all of them need `role:write`. */
export function isEditable(role: Role, canWrite: boolean): boolean {
  return canWrite && !role.system;
}
