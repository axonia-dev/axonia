import { useMemo } from "react";

import { useEdition } from "@/edition-context";
import {
  catalogForKind,
  composePermissionCatalog,
  type PermissionCatalog,
} from "@/features/admin/domain/edition-permissions";
import type { OrganizationKind } from "@/features/admin/domain/permission-catalog";

/** Every permission this edition's roles matrix can offer: the open catalog, and the groups its
 *  settings sections and its Notifications card contribute for what they gate. */
export function usePermissionCatalog(): PermissionCatalog {
  const { settingsSections, notificationsCard } = useEdition();
  return useMemo(
    () =>
      composePermissionCatalog([
        ...settingsSections.flatMap((section) =>
          section.permissionGroup === undefined
            ? []
            : [section.permissionGroup],
        ),
        ...(notificationsCard === null
          ? []
          : [notificationsCard.permissionGroup]),
      ]),
    [settingsSections, notificationsCard],
  );
}

/** The permissions that apply to an organization of this kind; an Enterprise one until the
 *  kind is known, as the page has always assumed. */
export function useApplicablePermissions(
  kind: OrganizationKind | undefined,
): PermissionCatalog {
  const catalog = usePermissionCatalog();
  const applicableKind = kind ?? "ENTERPRISE";
  return useMemo(
    () => catalogForKind(catalog, applicableKind),
    [catalog, applicableKind],
  );
}
