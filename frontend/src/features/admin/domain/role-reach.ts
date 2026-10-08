import type { Role } from "@/features/admin/domain/admin-user";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";

/** Whether the role grants the code. No role grants nothing, which is what a pane with no
 *  pinned comparison role holds. */
export function holds(role: Role | null, code: string): boolean {
  return role !== null && role.permissions.includes(code);
}

/** How deep a role reaches into one permission group. */
export type ReachLevel = "none" | "some" | "all";

function level(held: number, total: number): ReachLevel {
  if (held === 0) return "none";
  return held === total ? "all" : "some";
}

/**
 * One level per permission group that applies to the organization's kind, in catalog order.
 * One per group the applicable catalog draws.
 */
export function roleReach(
  role: Role,
  catalog: PermissionCatalog,
): ReachLevel[] {
  const held = new Set(role.permissions);
  return catalog.groups.map((group) =>
    level(
      group.permissions.filter((p) => held.has(p.code)).length,
      group.permissions.length,
    ),
  );
}

/**
 * True when the role grants something this organization's catalog carries and everything it
 * grants there is a read. A role holding nothing is empty rather than read-only, so it
 * answers false. A code the applicable catalog does not carry - one the matrix omits, or an
 * enterprise-only code held inside a hub - cannot make a role read-only any more than it can
 * make it writable, since the pane draws no switch for it either way.
 */
export function isReadOnly(role: Role, catalog: PermissionCatalog): boolean {
  const held = new Set(role.permissions);
  const applicable = catalog.entries.filter((p) => held.has(p.code));
  return applicable.length > 0 && applicable.every((p) => p.action === "read");
}

/**
 * How many of the permissions that apply to this organization the role grants. A code the
 * applicable catalog does not carry - `community:create`, which the matrix deliberately
 * omits, or an enterprise-only code held inside a hub - is not one the page can show, so
 * counting it would leave the headline disagreeing with the switches under it.
 */
export function heldCount(role: Role, catalog: PermissionCatalog): number {
  const held = new Set(role.permissions);
  return catalog.entries.filter((p) => held.has(p.code)).length;
}

/** The applicable codes exactly one of the two roles grants. */
export function differingPermissions(
  a: Role,
  b: Role,
  catalog: PermissionCatalog,
): string[] {
  const inA = new Set(a.permissions);
  const inB = new Set(b.permissions);
  return catalog.entries
    .filter((p) => inA.has(p.code) !== inB.has(p.code))
    .map((p) => p.code);
}
