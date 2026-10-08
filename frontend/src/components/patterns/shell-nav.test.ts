import { describe, expect, it } from "vitest";

import type { NavGroup, NavItem } from "@/components/patterns/nav-types";
import {
  isGroupActive,
  isItemActive,
  isPathActive,
  owningItem,
} from "@/components/patterns/shell-nav";

function item(overrides: Partial<NavItem> = {}): NavItem {
  return {
    to: "/c/guild/schedule",
    label: "Schedule",
    icon: null,
    ...overrides,
  };
}

describe("isPathActive", () => {
  it("matches the path itself", () => {
    expect(
      isPathActive("/c/guild/schedule", "/c/guild/schedule", false, []),
    ).toBe(true);
  });

  it("matches a path below it", () => {
    expect(
      isPathActive("/c/guild/library", "/c/guild/library/42", false, []),
    ).toBe(true);
  });

  // A bare startsWith lights `sessions` for a future `sessions-archive`, and the
  // bug would be silent. Matching stops at a segment boundary.
  it("does not match a sibling that merely shares a prefix", () => {
    expect(
      isPathActive("/c/guild/library", "/c/guild/library-types", false, []),
    ).toBe(false);
  });

  it("matches only exactly when end is set", () => {
    expect(isPathActive("/c/guild", "/c/guild", true, [])).toBe(true);
    expect(isPathActive("/c/guild", "/c/guild/schedule", true, [])).toBe(false);
  });

  it("matches a path the item owns", () => {
    expect(
      isPathActive("/c/guild/schedule", "/c/guild/sessions/41", false, [
        "/c/guild/sessions",
        "/c/guild/series",
      ]),
    ).toBe(true);
  });

  it("holds an owned path to the same segment boundary", () => {
    expect(
      isPathActive("/c/guild/schedule", "/c/guild/sessions-archive", false, [
        "/c/guild/sessions",
      ]),
    ).toBe(false);
  });

  it("consults owned paths even for an exact-match item", () => {
    expect(
      isPathActive("/c/guild/expertise", "/c/guild/skills/3", true, [
        "/c/guild/skills",
      ]),
    ).toBe(true);
  });
});

describe("isItemActive", () => {
  it("reads end and owns off the item", () => {
    const schedule = item({ owns: ["/c/guild/sessions"] });
    expect(isItemActive(schedule, "/c/guild/sessions/41")).toBe(true);
    expect(isItemActive(item({ end: true }), "/c/guild/schedule/x")).toBe(
      false,
    );
  });
});

describe("isGroupActive", () => {
  it("is active when a member item owns the path", () => {
    const group: NavGroup = {
      label: "Engage",
      items: [
        item({ owns: ["/c/guild/sessions"] }),
        item({ to: "/c/guild/backlog" }),
      ],
    };
    expect(isGroupActive(group, "/c/guild/sessions/41")).toBe(true);
    expect(isGroupActive(group, "/c/guild/library")).toBe(false);
  });
});

describe("owningItem", () => {
  const groups: NavGroup[] = [
    {
      label: "Engage",
      items: [
        item({ owns: ["/c/guild/sessions", "/c/guild/series"] }),
        item({ to: "/c/guild/backlog", label: "Backlog" }),
      ],
    },
  ];

  it("names the section a sub-page belongs under", () => {
    expect(owningItem(groups, "/c/guild/sessions/41")?.label).toBe("Schedule");
    expect(
      owningItem(groups, "/c/guild/series/7/occurrences/2026-08-13")?.label,
    ).toBe("Schedule");
  });

  // A section page is its own destination and has no parent to name.
  it("answers nothing for a section's own page", () => {
    expect(owningItem(groups, "/c/guild/schedule")).toBeUndefined();
  });

  it("answers nothing for an unowned page", () => {
    expect(owningItem(groups, "/c/guild/health")).toBeUndefined();
  });
});
