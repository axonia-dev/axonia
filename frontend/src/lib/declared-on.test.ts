import { describe, expect, it } from "vitest";

import { declaredMonth, declaredOn } from "@/lib/declared-on";

describe("declaredOn", () => {
  it("formats the declaration date as a day with a three-letter month", () => {
    expect(declaredOn("2026-09-12T12:00:00Z")).toBe("12 Sep 2026");
    expect(declaredOn("2026-06-01T12:00:00Z")).toBe("1 Jun 2026");
  });
});

describe("declaredMonth", () => {
  it("formats the declaration date as a three-letter month and a year", () => {
    expect(declaredMonth("2026-09-12T12:00:00Z")).toBe("Sep 2026");
    expect(declaredMonth("2026-03-02T09:00:00Z")).toBe("Mar 2026");
  });
});
