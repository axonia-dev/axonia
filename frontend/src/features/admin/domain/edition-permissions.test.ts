import { describe, expect, it } from "vitest";

import {
  catalogForKind,
  composePermissionCatalog,
  openPermissionCatalog,
  type PermissionGroupContribution,
  permissionIn,
} from "@/features/admin/domain/edition-permissions";
import {
  OPEN_GROUP_ORDER,
  PERMISSION_CATALOG,
  type PermissionDef,
} from "@/features/admin/domain/permission-catalog";

const AUDIT_READ: PermissionDef = {
  code: "audit:read",
  group: "Audit",
  action: "read",
  enterpriseOnly: true,
  label: "View the audit trail",
  description: "See who changed what.",
};

const AUDIT: PermissionGroupContribution = {
  follows: "Members",
  permissions: [AUDIT_READ],
};

function contributing(permission: PermissionDef): PermissionGroupContribution {
  return { follows: "Members", permissions: [permission] };
}

describe("the open permission catalog", () => {
  it("draws the open groups in their order, each holding its entries", () => {
    expect(openPermissionCatalog.groups.map((g) => g.label)).toEqual([
      ...OPEN_GROUP_ORDER,
    ]);
    expect(openPermissionCatalog.entries).toEqual(PERMISSION_CATALOG);
  });

  it("finds an entry by its code, and nothing for a code it does not carry", () => {
    expect(permissionIn(openPermissionCatalog, "member:read")?.label).toBe(
      "View members",
    );
    expect(permissionIn(openPermissionCatalog, "audit:read")).toBeUndefined();
  });
});

describe("a contributed permission group", () => {
  it("is drawn right after the open group it follows", () => {
    const catalog = composePermissionCatalog([AUDIT]);

    const labels = catalog.groups.map((g) => g.label);
    expect(labels.indexOf("Audit")).toBe(labels.indexOf("Members") + 1);
    expect(catalog.entries.map((p) => p.code)).toContain("audit:read");
    expect(permissionIn(catalog, "audit:read")?.label).toBe(
      "View the audit trail",
    );
  });

  it("is refused when it offers a code or a group twice", () => {
    expect(() => composePermissionCatalog([AUDIT, AUDIT])).toThrow(
      "Each permission and each permission group is offered once.",
    );
  });

  it("is refused when it re-offers an open code under a group of its own", () => {
    const reoffered = contributing({ ...AUDIT_READ, code: "member:read" });

    expect(() => composePermissionCatalog([reoffered])).toThrow(
      "Each permission and each permission group is offered once.",
    );
  });

  it("is refused when it labels a group of new codes like an open group", () => {
    const relabelled = contributing({ ...AUDIT_READ, group: "Members" });

    expect(() => composePermissionCatalog([relabelled])).toThrow(
      "Each permission and each permission group is offered once.",
    );
  });

  it("leaves a hub when it is enterprise-only, and with it the group it emptied", () => {
    const hub = catalogForKind(composePermissionCatalog([AUDIT]), "HUB");

    expect(hub.groups.map((g) => g.label)).not.toContain("Audit");
    expect(hub.entries.some((p) => p.enterpriseOnly)).toBe(false);
  });

  it("leaves an enterprise catalog exactly as composed", () => {
    const catalog = composePermissionCatalog([AUDIT]);

    expect(catalogForKind(catalog, "ENTERPRISE")).toBe(catalog);
  });
});
