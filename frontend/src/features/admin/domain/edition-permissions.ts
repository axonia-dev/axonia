import {
  OPEN_GROUP_ORDER,
  type OpenPermissionGroupLabel,
  type OrganizationKind,
  PERMISSION_CATALOG,
  type PermissionDef,
  type PermissionGroup,
} from "@/features/admin/domain/permission-catalog";

/** Permissions a closed slice gates its pages on, drawn as their own group right after the
 *  open group they follow. */
export interface PermissionGroupContribution {
  follows: OpenPermissionGroupLabel;
  permissions: ReadonlyArray<PermissionDef>;
}

/** What a roles matrix offers, flat and grouped, in the order it draws them. */
export interface PermissionCatalog {
  entries: ReadonlyArray<PermissionDef>;
  groups: ReadonlyArray<PermissionGroup>;
}

function groupLabelsOf(contribution: PermissionGroupContribution): string[] {
  return [...new Set(contribution.permissions.map((p) => p.group))];
}

function offeredOnce(values: ReadonlyArray<string>): boolean {
  return new Set(values).size === values.length;
}

/** The open catalog with each contributed group drawn after the open group it follows. */
export function composePermissionCatalog(
  contributions: ReadonlyArray<PermissionGroupContribution>,
): PermissionCatalog {
  const all = [
    ...PERMISSION_CATALOG,
    ...contributions.flatMap((c) => c.permissions),
  ];
  const labels = OPEN_GROUP_ORDER.flatMap((open) => [
    open,
    ...contributions
      .filter((c) => c.follows === open)
      .flatMap((c) => groupLabelsOf(c)),
  ]);
  if (!offeredOnce(all.map((p) => p.code)) || !offeredOnce(labels)) {
    throw new Error(
      "Each permission and each permission group is offered once.",
    );
  }
  const groups = labels.map((label) => ({
    key: label,
    label,
    permissions: all.filter((p) => p.group === label),
  }));
  return { entries: groups.flatMap((g) => g.permissions), groups };
}

export const openPermissionCatalog: PermissionCatalog =
  composePermissionCatalog([]);

/** The catalog as it applies to an organization of the given kind: a hub drops the
 *  enterprise-only permissions, and any group left empty with them. */
export function catalogForKind(
  catalog: PermissionCatalog,
  kind: OrganizationKind,
): PermissionCatalog {
  if (kind !== "HUB") return catalog;
  return {
    entries: catalog.entries.filter((p) => !p.enterpriseOnly),
    groups: catalog.groups
      .map((g) => ({
        ...g,
        permissions: g.permissions.filter((p) => !p.enterpriseOnly),
      }))
      .filter((g) => g.permissions.length > 0),
  };
}

export function permissionIn(
  catalog: PermissionCatalog,
  code: string,
): PermissionDef | undefined {
  return catalog.entries.find((p) => p.code === code);
}
