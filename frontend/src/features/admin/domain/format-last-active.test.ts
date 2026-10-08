import { describe, expect, it } from "vitest";

import { formatLastActive } from "@/features/admin/domain/format-last-active";

const NOW = new Date("2026-06-18T12:00:00Z");

describe("formatLastActive", () => {
  it("returns 'Active now' under 5 minutes", () => {
    const r = formatLastActive("2026-06-18T11:58:00Z", NOW);
    expect(r.label).toBe("Active now");
    expect(r.active).toBe(true);
  });

  it("returns relative hours within 24h and stays active", () => {
    const r = formatLastActive("2026-06-18T10:00:00Z", NOW);
    expect(r.label).toBe("Active 2h ago");
    expect(r.active).toBe(true);
  });

  it("returns relative minutes within the hour", () => {
    expect(formatLastActive("2026-06-18T11:40:00Z", NOW).label).toBe(
      "Active 20m ago",
    );
  });

  it("returns a date and goes inactive past 24h", () => {
    const r = formatLastActive("2026-03-14T09:00:00Z", NOW);
    expect(r.label).toBe("Last seen 14 Mar");
    expect(r.active).toBe(false);
  });

  it("handles null as never signed in", () => {
    expect(formatLastActive(null, NOW)).toEqual({
      label: "Never signed in",
      active: false,
    });
  });
});
