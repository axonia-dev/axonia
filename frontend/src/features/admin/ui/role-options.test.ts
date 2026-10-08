import { describe, expect, it } from "vitest";

import { knownCodes } from "@/features/admin/domain/ceiling";
import { openPermissionCatalog } from "@/features/admin/domain/edition-permissions";
import { roleOptions } from "@/features/admin/ui/role-options";

const KNOWN = knownCodes(openPermissionCatalog);

function role(id: number, name: string, permissions: string[]) {
  return { id, name, description: null, system: false, permissions };
}

describe("roleOptions", () => {
  it("carries a plain id/label option for a role within reach", () => {
    const options = roleOptions([role(1, "Auditor", ["member:read"])], {
      held: new Set(["member:read"]),
      known: KNOWN,
    });
    expect(options).toEqual([{ id: 1, label: "Auditor" }]);
  });

  it("marks a role beyond reach disabled with the shared reason", () => {
    const options = roleOptions([role(2, "Owner", ["organization:export"])], {
      held: new Set(["member:read"]),
      known: KNOWN,
    });
    expect(options).toEqual([
      {
        id: 2,
        label: "Owner",
        isDisabled: true,
        description: "Reaches beyond what this account holds.",
      },
    ]);
  });

  it("keeps each role's own reach independent of the others in the list", () => {
    const options = roleOptions(
      [
        role(1, "Auditor", ["member:read"]),
        role(2, "Owner", ["organization:export"]),
      ],
      { held: new Set(["member:read"]), known: KNOWN },
    );
    expect(options.find((o) => o.id === 1)?.isDisabled).toBeUndefined();
    expect(options.find((o) => o.id === 2)?.isDisabled).toBe(true);
  });

  it("leaves a role enabled whose only unheld code is one this edition does not know", () => {
    // sso:read is a Cloud code the open edition grants nothing by, so the server assigns past it
    const options = roleOptions(
      [role(3, "Restored", ["member:read", "sso:read"])],
      { held: new Set(["member:read"]), known: KNOWN },
    );
    expect(options).toEqual([{ id: 3, label: "Restored" }]);
  });
});
