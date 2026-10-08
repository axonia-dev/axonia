import type { Role } from "@/features/admin/domain/admin-user";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";

/** A role that survived the search, and how many of its grants the query matched. */
export interface RoleMatch {
  role: Role;
  grantsMatching: number;
}

/**
 * The applicable permissions whose label, code or description contains the query. Empty for
 * an empty query, which means "no highlight" rather than "everything".
 */
export function matchingPermissionCodes(
  q: string,
  catalog: PermissionCatalog,
): Set<string> {
  const needle = q.trim().toLowerCase();
  if (needle === "") return new Set();
  const hits = catalog.entries.filter((p) =>
    `${p.label} ${p.code} ${p.description}`.toLowerCase().includes(needle),
  );
  return new Set(hits.map((p) => p.code));
}

/** System roles first, then custom, alphabetical within each group. */
function byRailOrder(a: RoleMatch, b: RoleMatch): number {
  if (a.role.system !== b.role.system) return a.role.system ? -1 : 1;
  return a.role.name.localeCompare(b.role.name);
}

/**
 * The rail's rule: a role survives when its name matches, or when it grants a permission the
 * query matches. Answers "which roles can do this", so a permission the role merely lacks
 * never brings it into the list.
 */
export function matchRoles(
  roles: Role[],
  q: string,
  catalog: PermissionCatalog,
): RoleMatch[] {
  const needle = q.trim().toLowerCase();
  const codes = matchingPermissionCodes(q, catalog);
  return roles
    .map((role) => {
      // Counted over the distinct codes the role holds, as every other count on the page is,
      // so a payload repeating a code cannot make the rail disagree with the pane.
      const held = new Set(role.permissions);
      return {
        role,
        grantsMatching: [...held].filter((c) => codes.has(c)).length,
      };
    })
    .filter(
      ({ role, grantsMatching }) =>
        needle === "" ||
        role.name.toLowerCase().includes(needle) ||
        grantsMatching > 0,
    )
    .sort(byRailOrder);
}
