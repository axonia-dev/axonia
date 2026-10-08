import { describe, expect, it } from "vitest";

import type { Role } from "@/features/admin/domain/admin-user";
import {
  catalogForKind,
  openPermissionCatalog,
} from "@/features/admin/domain/edition-permissions";
import {
  matchingPermissionCodes,
  matchRoles,
} from "@/features/admin/domain/match-roles";

const ENTERPRISE = openPermissionCatalog;
const HUB = catalogForKind(openPermissionCatalog, "HUB");

const role = (
  id: number,
  name: string,
  permissions: string[] = [],
  system = false,
): Role => ({ id, name, description: null, system, permissions });

const roles = [
  role(1, "Zeta"),
  role(2, "Alpha", ["scim:read", "scim:write"]),
  role(3, "Beta", ["member:read"]),
  role(4, "Owner", ["scim:read"], true),
];

const names = (q: string) =>
  matchRoles(roles, q, ENTERPRISE).map((m) => m.role.name);

describe("matchRoles", () => {
  it("puts system roles first, then custom, alphabetical within each", () => {
    expect(names("")).toEqual(["Owner", "Alpha", "Beta", "Zeta"]);
  });

  it("matches a role name case-insensitively", () => {
    expect(names("BET")).toEqual(["Beta"]);
  });

  it("matches a role that grants a permission whose label matches", () => {
    // "Manage directory sync" and "View directory sync"
    expect(names("directory sync")).toEqual(["Owner", "Alpha"]);
  });

  it("matches a role that grants a permission whose code matches", () => {
    expect(names("member:read")).toEqual(["Beta"]);
  });

  it("counts how many of a role's grants matched", () => {
    const matched = matchRoles(roles, "directory", ENTERPRISE);
    expect(matched.map((m) => [m.role.name, m.grantsMatching])).toEqual([
      ["Owner", 1],
      ["Alpha", 2],
    ]);
  });

  it("counts a code the payload repeats once, as every other count on the page does", () => {
    const repeated = role(5, "Dup", ["scim:read", "scim:read"]);
    expect(matchRoles([repeated], "directory", ENTERPRISE)).toEqual([
      { role: repeated, grantsMatching: 1 },
    ]);
  });

  it("reports zero matching grants when the role matched on its name alone", () => {
    expect(matchRoles(roles, "Zeta", ENTERPRISE)).toEqual([
      { role: roles[0], grantsMatching: 0 },
    ]);
  });

  it("never matches on a permission the kind cannot hold", () => {
    expect(matchRoles(roles, "directory", HUB)).toEqual([]);
  });
});

describe("matchingPermissionCodes", () => {
  it("is empty for an empty query", () => {
    expect(matchingPermissionCodes("", ENTERPRISE).size).toBe(0);
  });

  it("matches on label, code and description alike", () => {
    expect(matchingPermissionCodes("scim:write", ENTERPRISE)).toEqual(
      new Set(["scim:write"]),
    );
    expect(
      matchingPermissionCodes("revoke SCIM provisioning", ENTERPRISE),
    ).toEqual(new Set(["scim:write"]));
  });

  it("is restricted to the applicable catalog", () => {
    expect(matchingPermissionCodes("directory", HUB).size).toBe(0);
  });
});
