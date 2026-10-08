import type { Role, RoleBinding } from "@/features/admin/domain/admin-user";
import type { PermissionCatalog } from "@/features/admin/domain/edition-permissions";
import { NOT_SURFACED } from "@/features/admin/domain/permission-catalog";

export interface BindingReach {
  outOfReach: boolean;
  roleResolved: boolean;
}

/** What a role is measured against: the caller's own codes, and every code this edition knows. */
export interface Ceiling {
  held: ReadonlySet<string>;
  known: ReadonlySet<string>;
}

/** Every code this edition's server knows, mirroring `KnownPermissions`: each one its roles
 *  matrix offers, whatever the organization's kind, and each one the gate enforces but no
 *  matrix offers. */
export function knownCodes(catalog: PermissionCatalog): ReadonlySet<string> {
  return new Set([...catalog.entries.map((p) => p.code), ...NOT_SURFACED]);
}

/** Whether every one of `codes` this edition knows is a permission the caller already holds.
 *  The core both the role-shaped and the code-shaped callers share: a person's effective role
 *  carries codes without being a Role. A code the edition does not know grants nothing, so the
 *  server leaves it out of the ceiling and so does this; one the matrix omits but the gate
 *  enforces, or an enterprise-only one inside a hub, still counts. Mirrors
 *  `RoleCeiling.permissionsBeyondCaller`. */
export function codesWithinReach(
  codes: readonly string[],
  ceiling: Ceiling,
): boolean {
  return codes.every(
    (code) => !ceiling.known.has(code) || ceiling.held.has(code),
  );
}

/** Whether every permission the role grants is one the caller already holds. */
export function isWithinReach(role: Role, ceiling: Ceiling): boolean {
  return codesWithinReach(role.permissions, ceiling);
}

/**
 * A bound role's reach, distinguishing a role the catalog could not resolve from one that
 * genuinely lies beyond reach. A caller must not report the ceiling as the cause when the
 * catalog, not the ceiling, is why the role cannot be measured.
 */
export function bindingReach(
  roleId: number,
  roles: readonly Role[],
  ceiling: Ceiling,
): BindingReach {
  const role = roles.find((r) => r.id === roleId);
  if (role === undefined) return { outOfReach: true, roleResolved: false };
  return { outOfReach: !isWithinReach(role, ceiling), roleResolved: true };
}

function waitingRoles(
  bindings: readonly RoleBinding[],
  roles: readonly Role[],
): Role[] | undefined {
  const resolved = bindings
    .map((b) => roles.find((r) => r.id === b.roleId))
    .filter((r): r is Role => r !== undefined);
  return resolved.length === bindings.length ? resolved : undefined;
}

/**
 * Whether releasing a held group would move authority past the caller's ceiling. The server
 * measures `confirmGroup` against every role bound to the key, not the one binding whose id
 * happens to address the call, so release is blocked unless every waiting role is within reach.
 *
 * `roleResolved` is false both for a key nothing is waiting on and for a binding naming a role
 * absent from the organization's catalog. Neither can be proven within reach, so both block
 * release, and in neither is the ceiling the reason: a caller states the ceiling only while
 * `roleResolved`, so an unresolved catalog is never reported as a ceiling refusal.
 */
export function groupReleaseReach(
  bindings: readonly RoleBinding[],
  roles: readonly Role[],
  ceiling: Ceiling,
): BindingReach {
  if (bindings.length === 0) return { outOfReach: true, roleResolved: false };
  const waiting = waitingRoles(bindings, roles);
  if (waiting === undefined) return { outOfReach: true, roleResolved: false };
  return {
    outOfReach: !waiting.every((r) => isWithinReach(r, ceiling)),
    roleResolved: true,
  };
}
