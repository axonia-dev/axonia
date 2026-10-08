import { describe, expect, it } from "vitest";

import { activityLookup } from "@/features/events/domain/schedule-activity";

describe("activityLookup", () => {
  it("reports activity for a listed year-month (monthIndex0 is 0-based)", () => {
    const hasActivity = activityLookup(["2024-06", "2022-03"]);
    expect(hasActivity(2024, 5)).toBe(true); // June = index 5
    expect(hasActivity(2024, 4)).toBe(false); // May
  });

  it("reports no activity for a year that has none", () => {
    const hasActivity = activityLookup(["2024-06"]);
    expect(hasActivity(2019, 0)).toBe(false);
    expect(hasActivity(2027, 11)).toBe(false);
  });

  it("reports no activity when the list is empty", () => {
    expect(activityLookup([])(2026, 0)).toBe(false);
  });
});
