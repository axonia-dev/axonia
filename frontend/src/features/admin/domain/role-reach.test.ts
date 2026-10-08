import { describe, expect, it } from "vitest";

import type { Role } from "@/features/admin/domain/admin-user";
import {
  catalogForKind,
  openPermissionCatalog,
} from "@/features/admin/domain/edition-permissions";
import {
  differingPermissions,
  heldCount,
  isReadOnly,
  roleReach,
} from "@/features/admin/domain/role-reach";

const ENTERPRISE = openPermissionCatalog;
const HUB = catalogForKind(openPermissionCatalog, "HUB");

const role = (permissions: string[]): Role => ({
  id: 1,
  name: "R",
  description: null,
  system: false,
  permissions,
});

describe("roleReach", () => {
  it("returns one level per applicable group: seven for an enterprise, five for a hub", () => {
    expect(roleReach(role([]), ENTERPRISE)).toHaveLength(7);
    expect(roleReach(role([]), HUB)).toHaveLength(5);
  });

  it("reports none, some and all per group", () => {
    // Members is the third enterprise group and holds exactly member:read and member:write.
    expect(roleReach(role([]), ENTERPRISE)[2]).toBe("none");
    expect(roleReach(role(["member:read"]), ENTERPRISE)[2]).toBe("some");
    expect(
      roleReach(role(["member:read", "member:write"]), ENTERPRISE)[2],
    ).toBe("all");
  });

  it("ignores a permission that does not apply to the kind", () => {
    // scim:write is enterprise-only, so inside a hub it lifts no bar at all.
    expect(roleReach(role(["scim:write"]), HUB)).toEqual([
      "none",
      "none",
      "none",
      "none",
      "none",
    ]);
  });
});

describe("heldCount", () => {
  it("counts only the codes the applicable catalog carries", () => {
    expect(heldCount(role(["member:read", "member:write"]), ENTERPRISE)).toBe(
      2,
    );
  });

  it("ignores a code the catalog does not carry, which the matrix cannot show", () => {
    expect(
      heldCount(role(["member:read", "community:create"]), ENTERPRISE),
    ).toBe(1);
  });

  it("ignores an enterprise-only code a hub role still holds", () => {
    expect(heldCount(role(["member:read", "scim:write"]), HUB)).toBe(1);
  });
});

describe("isReadOnly", () => {
  it("is true when every held permission is a read", () => {
    expect(isReadOnly(role(["member:read", "role:read"]), ENTERPRISE)).toBe(
      true,
    );
  });

  it("is false when the role holds a write or a manage", () => {
    expect(isReadOnly(role(["member:read", "member:write"]), ENTERPRISE)).toBe(
      false,
    );
    expect(isReadOnly(role(["role:write"]), ENTERPRISE)).toBe(false);
  });

  it("is false for a role that holds nothing, which is not read-only but empty", () => {
    expect(isReadOnly(role([]), ENTERPRISE)).toBe(false);
  });

  it("ignores a code the catalog does not describe", () => {
    expect(
      isReadOnly(role(["member:read", "community:create"]), ENTERPRISE),
    ).toBe(true);
  });

  it("is false for a hub role holding only an enterprise-only read, which is empty there", () => {
    expect(isReadOnly(role(["scim:read"]), HUB)).toBe(false);
  });

  it("is true for an enterprise role holding the same codes", () => {
    expect(isReadOnly(role(["scim:read"]), ENTERPRISE)).toBe(true);
  });

  it("is true inside a hub for a role whose only write is enterprise-only", () => {
    // The hub catalog drops scim:write, so the one applicable code left is a read and the
    // pane draws no switch the badge could contradict. heldCount agrees: it counts 1.
    const hubRole = role(["member:read", "scim:write"]);
    expect(isReadOnly(hubRole, HUB)).toBe(true);
    expect(heldCount(hubRole, HUB)).toBe(1);
    expect(isReadOnly(hubRole, ENTERPRISE)).toBe(false);
  });
});

describe("differingPermissions", () => {
  it("returns the codes the two roles disagree on", () => {
    const a = role(["member:read", "member:write"]);
    const b = role(["member:read", "role:read"]);
    expect(
      differingPermissions(a, b, ENTERPRISE).sort((x, y) => x.localeCompare(y)),
    ).toEqual(["member:write", "role:read"]);
  });

  it("never reports a permission the kind cannot hold", () => {
    const a = role(["scim:write"]);
    const b = role([]);
    expect(differingPermissions(a, b, HUB)).toEqual([]);
    expect(differingPermissions(a, b, ENTERPRISE)).toEqual(["scim:write"]);
  });

  it("is empty for two identical roles", () => {
    const a = role(["member:read"]);
    expect(differingPermissions(a, role(["member:read"]), ENTERPRISE)).toEqual(
      [],
    );
  });
});
