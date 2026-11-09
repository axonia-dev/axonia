import { describe, expect, it } from "vitest";

import {
  cadenceLabel,
  monthlyRuleLabel,
} from "@/features/events/domain/cadence-label";

describe("monthlyRuleLabel", () => {
  it("names the weekday by its position in the month", () => {
    expect(monthlyRuleLabel("WEEKDAY_OF_MONTH", "2027-01-13")).toBe(
      "on the second Wednesday",
    );
  });

  it("reads a fifth weekday as the last", () => {
    expect(monthlyRuleLabel("WEEKDAY_OF_MONTH", "2027-01-29")).toBe(
      "on the last Friday",
    );
  });

  it("names the day number", () => {
    expect(monthlyRuleLabel("DAY_OF_MONTH", "2027-01-13")).toBe("on day 13");
  });

  it("falls back to a generic reading before a start date is picked", () => {
    expect(monthlyRuleLabel("WEEKDAY_OF_MONTH", "")).toBe(
      "on the same weekday",
    );
    expect(monthlyRuleLabel("DAY_OF_MONTH", "")).toBe("on the same day");
  });
});

describe("cadenceLabel", () => {
  const base = {
    monthlyBy: "WEEKDAY_OF_MONTH",
    startsOn: "2027-01-13",
  } as const;

  it("states the monthly rule for a monthly cadence", () => {
    expect(cadenceLabel({ ...base, count: 1, unit: "MONTH" })).toBe(
      "Every month on the second Wednesday",
    );
    expect(
      cadenceLabel({
        ...base,
        count: 2,
        unit: "MONTH",
        monthlyBy: "DAY_OF_MONTH",
      }),
    ).toBe("Every 2 months on day 13");
  });

  it("leaves every other unit bare", () => {
    expect(cadenceLabel({ ...base, count: 3, unit: "WEEK" })).toBe(
      "Every 3 weeks",
    );
    expect(cadenceLabel({ ...base, count: 1, unit: "DAY" })).toBe("Every day");
  });

  it("asks for a valid count", () => {
    expect(cadenceLabel({ ...base, count: 0, unit: "WEEK" })).toBe(
      "Enter a whole number of 1 or more",
    );
  });
});
