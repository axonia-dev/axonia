import { BookMarked } from "lucide-react";
import { describe, expect, it } from "vitest";

import { organizationSettingsGroups } from "@/components/patterns/settings-nav";
import type { SettingsSection } from "@/edition";
import type { OrganizationKind } from "@/features/admin/domain/permission-catalog";

const JOINING: Pick<SettingsSection, "group" | "joins"> = {
  group: {
    label: "People & access",
    items: [
      {
        path: "ledger",
        label: "Ledger",
        icon: BookMarked,
        permissions: ["role:read"],
      },
    ],
  },
  joins: "people-and-access",
};

function peopleAndAccess(kind: OrganizationKind): string[] {
  const [, people] = organizationSettingsGroups(kind, [JOINING]);
  return people?.items.map((item) => item.label) ?? [];
}

describe("a section that joins People & access", () => {
  it("sits after Group access and ahead of Directory sync", () => {
    expect(peopleAndAccess("ENTERPRISE")).toEqual([
      "Users",
      "Roles",
      "Group access",
      "Ledger",
      "Directory sync",
    ]);
  });

  // Placed by the open catalogue, not anchored on Group access, which a Hub does not have.
  it("keeps its place for a Hub", () => {
    expect(peopleAndAccess("HUB")).toEqual([
      "Users",
      "Roles",
      "Ledger",
      "Directory sync",
    ]);
  });

  it("adds no group of its own", () => {
    expect(
      organizationSettingsGroups("ENTERPRISE", [JOINING]).map(
        (group) => group.label,
      ),
    ).toEqual(["Organization", "People & access", "Data"]);
  });
});
