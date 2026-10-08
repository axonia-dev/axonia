import { describe, expect, it } from "vitest";

import { monthGrid, monthWindow } from "@/features/events/domain/month-grid";

// June 2026 starts on a Monday — ideal for testing the zero-offset case.
describe("monthGrid: June 2026 (starts Monday)", () => {
  const cells = monthGrid(2026, 5);

  it("first cell is 2026-06-01 and is inMonth", () => {
    expect(cells[0]).toEqual({ isoDate: "2026-06-01", inMonth: true });
  });

  it("has 35 cells (5 complete weeks)", () => {
    expect(cells).toHaveLength(35);
  });

  it("all 30 June days are inMonth=true", () => {
    expect(cells.filter((c) => c.inMonth)).toHaveLength(30);
  });

  it("trailing cells are July days with inMonth=false", () => {
    // June has 30 days starting Monday → 5 trailing (Jul 1–5)
    const trailing = cells.slice(30);
    expect(trailing).toHaveLength(5);
    trailing.forEach((c) => expect(c.inMonth).toBe(false));
    expect(trailing[0]!.isoDate).toBe("2026-07-01");
    expect(trailing[4]!.isoDate).toBe("2026-07-05");
  });

  it("cells are consecutive calendar days", () => {
    for (let i = 1; i < cells.length; i++) {
      const prev = new Date(cells[i - 1]!.isoDate + "T00:00:00Z").getTime();
      const curr = new Date(cells[i]!.isoDate + "T00:00:00Z").getTime();
      expect(curr - prev).toBe(86_400_000);
    }
  });
});

// May 2026 starts on Friday — exercises a non-zero leading offset.
describe("monthGrid: May 2026 (starts Friday)", () => {
  const cells = monthGrid(2026, 4);

  it("first cell is Monday 2026-04-27", () => {
    expect(cells[0]!.isoDate).toBe("2026-04-27");
    expect(cells[0]!.inMonth).toBe(false);
  });

  it("has 35 cells (5 complete weeks)", () => {
    expect(cells).toHaveLength(35);
  });

  it("all 31 May days are inMonth=true", () => {
    expect(cells.filter((c) => c.inMonth)).toHaveLength(31);
  });
});

// January 2023 starts on Sunday — exercises a 6-day leading offset (6 weeks).
describe("monthGrid: January 2023 (starts Sunday, 6 weeks)", () => {
  const cells = monthGrid(2023, 0);

  it("first cell is Monday 2022-12-26", () => {
    expect(cells[0]!.isoDate).toBe("2022-12-26");
  });

  it("has 42 cells (6 complete weeks)", () => {
    expect(cells).toHaveLength(42);
  });
});

describe("monthWindow", () => {
  it("June 2026 window spans from 2026-06-01T00:00:00Z to 2026-07-01T00:00:00Z", () => {
    expect(monthWindow(2026, 5)).toEqual({
      fromIso: "2026-06-01T00:00:00.000Z",
      toIso: "2026-07-01T00:00:00.000Z",
    });
  });

  it("December 2025 toIso rolls over to January 2026", () => {
    expect(monthWindow(2025, 11).toIso).toBe("2026-01-01T00:00:00.000Z");
  });
});
