import { describe, expect, it } from "vitest";

import {
  bindingReach,
  type Ceiling,
  codesWithinReach,
  groupReleaseReach,
  isWithinReach,
  knownCodes,
} from "@/features/admin/domain/ceiling";
import {
  composePermissionCatalog,
  openPermissionCatalog,
} from "@/features/admin/domain/edition-permissions";

/** The caller's codes, measured over what the open edition knows: no sso:* or billing:*. */
function holding(codes: string[]): Ceiling {
  return { held: new Set(codes), known: knownCodes(openPermissionCatalog) };
}

function role(permissions: string[]) {
  return {
    id: 1,
    name: "Support Lead",
    description: null,
    system: false,
    permissions,
  };
}

describe("isWithinReach", () => {
  it("is false when anything the role grants is unheld", () => {
    expect(
      isWithinReach(
        role(["member:read", "organization:export"]),
        holding(["member:read"]),
      ),
    ).toBe(false);
  });

  it("is false on a role blocked solely by a code the frontend catalog does not describe", () => {
    // community:create is enforced by the gate and deliberately absent from the roles matrix,
    // so it is the role's only unheld code. The server still knows it, so a predicate that
    // knew only the catalog would see nothing left to measure and report this role as reachable.
    expect(
      isWithinReach(role(["community:create"]), holding(["member:read"])),
    ).toBe(false);
  });

  it("is true for a role granting nothing", () => {
    expect(isWithinReach(role([]), holding([]))).toBe(true);
  });

  it("is true on a role whose only unheld code is one this edition does not know", () => {
    // sso:read is a Cloud code; under the open catalog it grants nothing, so the server's
    // RoleCeiling leaves it out and the role is assignable by a caller holding member:read.
    expect(
      isWithinReach(
        role(["member:read", "sso:read"]),
        holding(["member:read"]),
      ),
    ).toBe(true);
  });
});

describe("codesWithinReach", () => {
  it("is false when a code is not held", () => {
    expect(
      codesWithinReach(
        ["member:read", "organization:export"],
        holding(["member:read"]),
      ),
    ).toBe(false);
  });

  it("is true when every code is held", () => {
    expect(
      codesWithinReach(
        ["member:read"],
        holding(["member:read", "organization:export"]),
      ),
    ).toBe(true);
  });

  it("is false on a code the frontend catalog does not describe", () => {
    // Same guarantee, code-shaped: the server measures the ceiling over every code it knows,
    // community:create among them, so a catalog-filtered predicate would answer true.
    expect(
      codesWithinReach(["community:create"], holding(["member:read"])),
    ).toBe(false);
  });

  it("ignores a code this edition does not know, as the server does", () => {
    expect(codesWithinReach(["sso:read"], holding([]))).toBe(true);
  });

  it("is true for an empty code list", () => {
    expect(codesWithinReach([], holding([]))).toBe(true);
  });
});

describe("bindingReach", () => {
  const roles = [
    {
      id: 10,
      name: "Support Lead",
      description: null,
      system: false,
      permissions: ["member:read"],
    },
    {
      id: 20,
      name: "Owner",
      description: null,
      system: false,
      permissions: ["organization:export"],
    },
  ];

  it("resolves a role within reach", () => {
    expect(bindingReach(10, roles, holding(["member:read"]))).toEqual({
      outOfReach: false,
      roleResolved: true,
    });
  });

  it("resolves a role beyond reach", () => {
    expect(bindingReach(20, roles, holding(["member:read"]))).toEqual({
      outOfReach: true,
      roleResolved: true,
    });
  });

  it("reports a role the catalog cannot resolve as blocked but not resolved", () => {
    expect(bindingReach(999, roles, holding(["member:read"]))).toEqual({
      outOfReach: true,
      roleResolved: false,
    });
  });
});

describe("groupReleaseReach", () => {
  const roles = [
    {
      id: 10,
      name: "Support Lead",
      description: null,
      system: false,
      permissions: ["member:read"],
    },
    {
      id: 20,
      name: "Owner",
      description: null,
      system: false,
      permissions: ["organization:export"],
    },
  ];

  function binding(id: number, roleId: number) {
    return {
      id,
      subjectType: "GROUP",
      subjectId: "grp-eng",
      roleId,
      subjectLabel: null,
    };
  }

  it("blocks release when there is no waiting role to measure, rather than vacuously permitting it", () => {
    expect(groupReleaseReach([], [], holding([]))).toEqual({
      outOfReach: true,
      roleResolved: false,
    });
  });

  it("blocks release without resolving when a waiting role is absent from the catalog", () => {
    expect(
      groupReleaseReach(
        [binding(1, 999)],
        roles,
        holding(["member:read", "organization:export"]),
      ),
    ).toEqual({ outOfReach: true, roleResolved: false });
  });

  it("blocks release, ceiling stated, when a waiting role reaches past the caller", () => {
    expect(
      groupReleaseReach([binding(1, 20)], roles, holding(["member:read"])),
    ).toEqual({ outOfReach: true, roleResolved: true });
  });

  it("measures every role bound to the key, not just the one addressing the call", () => {
    // The server guards confirmGroup over the whole key, so one in-reach binding beside an
    // out-of-reach one must still block.
    expect(
      groupReleaseReach(
        [binding(1, 10), binding(2, 20)],
        roles,
        holding(["member:read"]),
      ),
    ).toEqual({ outOfReach: true, roleResolved: true });
  });

  it("permits release when every waiting role is within reach", () => {
    expect(
      groupReleaseReach(
        [binding(1, 10), binding(2, 20)],
        roles,
        holding(["member:read", "organization:export"]),
      ),
    ).toEqual({ outOfReach: false, roleResolved: true });
  });
});

describe("knownCodes", () => {
  it("knows every code the edition's matrix offers, and community:create, which none offers", () => {
    const actual = knownCodes(openPermissionCatalog);

    expect([...actual]).toEqual(
      expect.arrayContaining(["member:read", "scim:write", "community:create"]),
    );
    expect(actual.has("sso:read")).toBe(false);
  });

  it("knows a code an edition contributes", () => {
    const catalog = composePermissionCatalog([
      {
        follows: "Members",
        permissions: [
          {
            code: "sso:read",
            group: "Single sign-on",
            action: "read",
            enterpriseOnly: true,
            label: "View single sign-on",
            description: "See how members sign in.",
          },
        ],
      },
    ]);

    expect(knownCodes(catalog).has("sso:read")).toBe(true);
  });
});
