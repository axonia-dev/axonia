import { describe, expect, it } from "vitest";

import {
  catalogForKind,
  openPermissionCatalog,
  permissionIn,
} from "@/features/admin/domain/edition-permissions";
import {
  OPEN_GROUP_ORDER,
  PERMISSION_CATALOG,
  togglePermission,
} from "@/features/admin/domain/permission-catalog";

describe("permission catalog", () => {
  it("contains the 20 open codes and none of the retired ones", () => {
    const byCode = (a: string, b: string) => a.localeCompare(b);
    const codes = [...PERMISSION_CATALOG.map((p) => p.code)].sort(byCode);
    expect(codes).toEqual(
      [
        "communication:write",
        "community_kind:read",
        "community_kind:write",
        "library_type:read",
        "library_type:write",
        "member:read",
        "member:write",
        "organization:delete",
        "organization:export",
        "organization:write",
        "ownership:transfer",
        "reaction:read",
        "reaction:write",
        "role:assign",
        "role:read",
        "role:write",
        "scim:read",
        "scim:write",
        "section_type:read",
        "section_type:write",
      ].sort(byCode),
    );
    expect(codes).not.toContain("organization:read");
  });

  it("gives every permission a non-empty label, description and group", () => {
    for (const p of PERMISSION_CATALOG) {
      expect(p.label.length).toBeGreaterThan(0);
      expect(p.description.length).toBeGreaterThan(0);
      expect(p.group.length).toBeGreaterThan(0);
    }
  });

  it("files every entry under one of the open groups", () => {
    const groups: ReadonlyArray<string> = OPEN_GROUP_ORDER;
    expect(PERMISSION_CATALOG.filter((p) => !groups.includes(p.group))).toEqual(
      [],
    );
  });

  it("marks the dangerous ops owner-only", () => {
    expect(
      permissionIn(openPermissionCatalog, "organization:delete")?.ownerOnly,
    ).toBe(true);
    expect(
      permissionIn(openPermissionCatalog, "ownership:transfer")?.ownerOnly,
    ).toBe(true);
    expect(
      permissionIn(openPermissionCatalog, "member:read")?.ownerOnly,
    ).toBeFalsy();
  });

  it("groups permissions in display order", () => {
    expect(openPermissionCatalog.groups.map((g) => g.label)).toEqual([
      "Workspace",
      "Communication",
      "Members",
      "Roles & access",
      "Directory sync",
      "Catalog",
      "Data",
    ]);
  });

  it("togglePermission adds when absent and removes when present", () => {
    expect(togglePermission([], "member:read")).toEqual(["member:read"]);
    expect(togglePermission(["member:read"], "member:read")).toEqual([]);
  });
});

describe("kind-aware catalog", () => {
  it("flags exactly the five open enterprise-only codes", () => {
    const flagged = PERMISSION_CATALOG.filter((p) => p.enterpriseOnly).map(
      (p) => p.code,
    );
    expect(new Set(flagged)).toEqual(
      new Set([
        "scim:read",
        "scim:write",
        "community_kind:read",
        "community_kind:write",
        "organization:export",
      ]),
    );
  });

  it("a hub's catalog drops the enterprise-only codes", () => {
    const codes = catalogForKind(openPermissionCatalog, "HUB").entries.map(
      (p) => p.code,
    );
    expect(codes).toHaveLength(15);
    expect(codes).not.toContain("scim:write");
    expect(codes).not.toContain("scim:read");
    expect(codes).not.toContain("community_kind:read");
    expect(codes).toContain("library_type:read");
    expect(codes).toContain("section_type:read");
  });

  // The roundup half of what it governs is enterprise-only, but the organization-wide
  // mail kill switch it absorbs governs hub mail too, so a hub holds it for that alone.
  it("keeps Communication in a hub", () => {
    expect(
      permissionIn(openPermissionCatalog, "communication:write")
        ?.enterpriseOnly,
    ).toBeFalsy();
    expect(
      catalogForKind(openPermissionCatalog, "HUB").groups.map((g) => g.label),
    ).toContain("Communication");
  });

  it("a hub's groups drop Directory sync but keep a trimmed Catalog", () => {
    const groups = catalogForKind(openPermissionCatalog, "HUB").groups;
    const labels = groups.map((g) => g.label);
    expect(labels).not.toContain("Directory sync");
    const catalog = groups.find((g) => g.label === "Catalog");
    expect(catalog).toBeDefined();
    expect(catalog?.permissions.map((p) => p.code)).toEqual([
      "library_type:read",
      "library_type:write",
      "section_type:read",
      "section_type:write",
      "reaction:read",
      "reaction:write",
    ]);
  });

  it("an enterprise's catalog is the open catalog", () => {
    expect(catalogForKind(openPermissionCatalog, "ENTERPRISE")).toBe(
      openPermissionCatalog,
    );
  });
});
