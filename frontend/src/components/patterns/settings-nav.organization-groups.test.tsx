import { describe, expect, it } from "vitest";

import {
  organizationSettingsGroups,
  visibleGroups,
} from "@/components/patterns/settings-nav";

// Split out of settings-nav.test.tsx (300-line ceiling): organizationSettingsGroups is the
// larger half of that file's catalogue coverage, split cleanly along the account/organization
// boundary the module itself draws.

function labels(groups: ReturnType<typeof organizationSettingsGroups>) {
  return groups.flatMap((group) => group.items.map((item) => item.label));
}

describe("organizationSettingsGroups", () => {
  it("names every group by what it holds, for either kind", () => {
    expect(
      organizationSettingsGroups("ENTERPRISE", []).map((group) => group.label),
    ).toEqual(["Organization", "People & access", "Data"]);
    expect(
      organizationSettingsGroups("HUB", []).map((group) => group.label),
    ).toEqual(["Community", "People & access", "Data"]);
  });

  it("never surfaces a Security or Configuration group, for either kind", () => {
    const allLabels = [
      ...organizationSettingsGroups("ENTERPRISE", []).map(
        (group) => group.label,
      ),
      ...organizationSettingsGroups("HUB", []).map((group) => group.label),
    ];
    expect(allLabels).not.toContain("Security");
    expect(allLabels).not.toContain("Configuration");
  });

  it("draws no chrome on the lead group and a divider on Data", () => {
    const groups = organizationSettingsGroups("ENTERPRISE", []);
    expect(groups[0]?.chrome).toBe("none");
    expect(groups[2]?.chrome).toBe("divider");
  });

  it("names the tenant section General regardless of kind, not the group's own heading again", () => {
    const [enterpriseLead] = organizationSettingsGroups("ENTERPRISE", []);
    const [hubLead] = organizationSettingsGroups("HUB", []);
    expect(enterpriseLead?.items[0]?.label).toBe("General");
    expect(hubLead?.items[0]?.label).toBe("General");
    expect(enterpriseLead?.items[0]?.path).toBe("general");
    expect(hubLead?.items[0]?.path).toBe("general");
  });

  it("leads with General, Vocabulary and Notifications", () => {
    const [lead] = organizationSettingsGroups("ENTERPRISE", []);
    expect(lead?.items.map((item) => item.label)).toEqual([
      "General",
      "Vocabulary",
      "Notifications",
    ]);
    expect(lead?.items.map((item) => item.path)).toEqual([
      "general",
      "vocabulary",
      "notifications",
    ]);
  });

  it("lists no separate type sections", () => {
    const enterpriseLabels = labels(
      organizationSettingsGroups("ENTERPRISE", []),
    );
    for (const label of ["Community types", "Library types", "Section types"]) {
      expect(enterpriseLabels).not.toContain(label);
    }
  });

  it("shows Vocabulary to a caller holding any one catalogue read", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", []),
      {
        can: (permission) => permission === "section_type:read",
        delegated: false,
      },
    );
    expect(labels(visible)).toContain("Vocabulary");
  });

  it("hides Vocabulary from a caller holding none of the three", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", []),
      {
        can: (permission) => permission === "member:read",
        delegated: false,
      },
    );
    expect(labels(visible)).not.toContain("Vocabulary");
  });

  it("shows Notifications to a holder of a permission an edition's card reveals", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", [], ["ledger:read"]),
      {
        can: (permission) => permission === "ledger:read",
        delegated: false,
      },
    );
    expect(labels(visible)).toContain("Notifications");
  });

  it("still shows Notifications to a holder of communication:write alone", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", [], ["ledger:read"]),
      {
        can: (permission) => permission === "communication:write",
        delegated: false,
      },
    );
    expect(labels(visible)).toContain("Notifications");
  });

  it("hides Notifications from a caller holding neither", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", [], ["ledger:read"]),
      {
        can: (permission) => permission === "member:read",
        delegated: false,
      },
    );
    expect(labels(visible)).not.toContain("Notifications");
  });

  it("leaves the tenant section ungated: no permissions field", () => {
    const [enterpriseTenant] = organizationSettingsGroups("ENTERPRISE", []);
    const [hubTenant] = organizationSettingsGroups("HUB", []);
    expect(enterpriseTenant?.items[0]?.permissions).toBeUndefined();
    expect(hubTenant?.items[0]?.permissions).toBeUndefined();
  });

  it("keeps Data identical across kinds", () => {
    const data = (kind: "ENTERPRISE" | "HUB") =>
      organizationSettingsGroups(kind, [])
        .filter((group) => group.label === "Data")
        .map((group) => ({
          label: group.label,
          requiresOwnSession: group.requiresOwnSession,
          items: labels([group]),
        }));
    expect(data("ENTERPRISE")).toEqual(data("HUB"));

    const [, , enterpriseData] = organizationSettingsGroups("ENTERPRISE", []);
    expect(enterpriseData?.label).toBe("Data");
    expect(enterpriseData?.requiresOwnSession).toBe(true);
    expect(enterpriseData?.items.map((item) => item.label)).toEqual([
      "Data export",
    ]);
  });

  it("lists the whole People & access section for an enterprise", () => {
    const [, peopleAccess] = organizationSettingsGroups("ENTERPRISE", []);
    expect(peopleAccess?.items.map((item) => item.label)).toEqual([
      "Users",
      "Roles",
      "Group access",
      "Directory sync",
    ]);
  });

  // A hub brokers no directory, so there is no group for a binding to name: the backend refuses
  // to write one and serves no listing. Directory sync drops out on its own, because scim:read is
  // an enterprise-only permission a hub role can never hold; Group access rides role:read, which a
  // hub genuinely needs for the Roles page, so it is the one row the permission filter cannot
  // reach and the catalogue has to drop by kind.
  it("drops Group access from a hub, keeping the rows a hub can use", () => {
    const [, peopleAccess] = organizationSettingsGroups("HUB", []);
    expect(peopleAccess?.items.map((item) => item.label)).toEqual([
      "Users",
      "Roles",
      "Directory sync",
    ]);
  });
});
