import { Building2 } from "lucide-react";
import { describe, expect, it } from "vitest";

import type { NavItem } from "@/components/patterns/nav-types";
import {
  organizationSettingsGroups,
  organizationSettingsTitle,
  toNavGroups,
  visibleGroups,
} from "@/components/patterns/settings-nav";
import { isItemActive } from "@/components/patterns/shell-nav";

const allowAll = { can: () => true, delegated: false };

function labels(groups: ReturnType<typeof organizationSettingsGroups>) {
  return groups.flatMap((group) => group.items.map((item) => item.label));
}

function paths(groups: ReturnType<typeof organizationSettingsGroups>) {
  return groups.flatMap((group) => group.items.map((item) => item.path));
}

describe("settings paths", () => {
  it("are always relative: no path starts with a slash", () => {
    const allPaths = [
      ...paths(organizationSettingsGroups("ENTERPRISE", [])),
      ...paths(organizationSettingsGroups("HUB", [])),
    ];
    expect(allPaths.length).toBeGreaterThan(0);
    expect(allPaths.every((path) => !path.startsWith("/"))).toBe(true);
  });
});

describe("visibleGroups", () => {
  it("keeps the ungated tenant section even when every permission is refused", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", []),
      {
        can: () => false,
        delegated: false,
      },
    );
    expect(visible.map((group) => group.label)).toEqual(["Organization"]);
    expect(labels(visible)).toEqual(["General"]);
  });

  it("keeps the ungated Community section for a hub with no permissions", () => {
    const visible = visibleGroups(organizationSettingsGroups("HUB", []), {
      can: () => false,
      delegated: false,
    });
    expect(visible.map((group) => group.label)).toEqual(["Community"]);
    expect(labels(visible)).toEqual(["General"]);
  });

  it("drops sections whose permission is missing and groups left empty, keeping the tenant section", () => {
    const only = (permission: string) => permission === "member:read";
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", []),
      {
        can: only,
        delegated: false,
      },
    );
    expect(visible.map((group) => group.label)).toEqual([
      "Organization",
      "People & access",
    ]);
    expect(labels(visible)).toEqual(["General", "Users"]);
  });

  it("keeps Roles for a caller who may only read roles, so the page's read-only view is reachable", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", []),
      {
        can: (permission) => permission === "role:read",
        delegated: false,
      },
    );
    expect(labels(visible)).toContain("Roles");
  });

  it("drops an own-session group entirely for a delegated tab, keeping the ungated tenant section", () => {
    const visible = visibleGroups(
      organizationSettingsGroups("ENTERPRISE", []),
      {
        can: () => true,
        delegated: true,
      },
    );
    expect(visible.map((group) => group.label)).toEqual([
      "Organization",
      "People & access",
    ]);
  });
});

describe("organizationSettingsTitle", () => {
  it("names the surface Organization settings for an enterprise", () => {
    expect(organizationSettingsTitle("ENTERPRISE")).toBe(
      "Organization settings",
    );
  });

  it("names the surface Community settings for a hub", () => {
    expect(organizationSettingsTitle("HUB")).toBe("Community settings");
  });
});

describe("toNavGroups", () => {
  it("prefixes every path with the base, including the tenant section", () => {
    const [tenant] = toNavGroups(
      visibleGroups(organizationSettingsGroups("ENTERPRISE", []), allowAll),
      "/settings/organization",
    );
    expect(tenant?.items[0]?.to).toBe("/settings/organization/general");
  });

  it("defaults a group among others to a drawn header", () => {
    const groups = toNavGroups(
      visibleGroups(organizationSettingsGroups("ENTERPRISE", []), allowAll),
      "/settings/organization",
    );
    expect(groups[1]?.chrome).toBe("header");
  });

  it("draws no mark above a group that is the only one", () => {
    const [only] = toNavGroups(
      [
        {
          label: "Solo",
          items: [{ path: "solo", label: "Solo", icon: Building2 }],
        },
      ],
      "/settings/account",
    );
    expect(only?.chrome).toBe("none");
  });

  it("keeps a nested section lit while one of its sub-pages is open", () => {
    const [lead] = toNavGroups(
      visibleGroups(organizationSettingsGroups("ENTERPRISE", []), allowAll),
      "/settings/organization",
    );
    const vocabulary = lead?.items.find((item) => item.label === "Vocabulary");
    expect(
      isItemActive(
        vocabulary as NavItem,
        "/settings/organization/vocabulary/community-types",
      ),
    ).toBe(true);
  });

  it("matches a leaf section on its own path alone", () => {
    const [lead] = toNavGroups(
      visibleGroups(organizationSettingsGroups("ENTERPRISE", []), allowAll),
      "/settings/organization",
    );
    const general = lead?.items.find((item) => item.label === "General");
    expect(
      isItemActive(general as NavItem, "/settings/organization/general/nested"),
    ).toBe(false);
  });

  it("lets a group's own chrome win over the sole-group default", () => {
    const [only] = toNavGroups(
      [{ label: "Solo", chrome: "divider", items: [] }],
      "/settings/organization",
    );
    expect(only?.chrome).toBe("divider");
  });
});

describe("visibleGroups permission filtering", () => {
  const multi = [
    {
      label: "Synthetic",
      items: [
        {
          path: "many",
          label: "Many",
          icon: Building2,
          permissions: ["a:read", "b:read", "c:read"],
        },
        {
          path: "one",
          label: "One",
          icon: Building2,
          permissions: ["d:read"],
        },
        {
          path: "open",
          label: "Open",
          icon: Building2,
        },
      ],
    },
  ];

  it("keeps a section when the caller holds any one of its permissions", () => {
    const visible = visibleGroups(multi, {
      can: (permission) => permission === "c:read",
      delegated: false,
    });
    expect(labels(visible)).toEqual(["Many", "Open"]);
  });

  it("drops a section when the caller holds none of its permissions", () => {
    const visible = visibleGroups(multi, {
      can: (permission) => permission === "z:read",
      delegated: false,
    });
    expect(labels(visible)).toEqual(["Open"]);
  });
});
