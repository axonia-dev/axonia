import type { SelectOption } from "@/components/ui/Select";
import type { Role } from "@/features/admin/domain/admin-user";
import { type Ceiling, isWithinReach } from "@/features/admin/domain/ceiling";
import { REACHES_BEYOND } from "@/features/admin/ui/ceiling-copy";

/** The role picker's option array, shared by every role picker so the disabled reason cannot
 *  drift between them. A role beyond the caller's reach stays listed, but disabled with why. */
export function roleOptions(
  roles: readonly Role[],
  ceiling: Ceiling,
): SelectOption<number>[] {
  return roles.map((role) => {
    const option: SelectOption<number> = { id: role.id, label: role.name };
    if (!isWithinReach(role, ceiling)) {
      option.isDisabled = true;
      option.description = REACHES_BEYOND;
    }
    return option;
  });
}
