import { describe, expect, it } from "vitest";

import { dropsFor, MAX_DROPS } from "@/features/expertise/ui/echo-drops";
import { ECHO } from "@/features/expertise/ui/echo-timeline";

const at = (
  signalId: number,
  tag: string,
  x: number,
  y: number,
  ...[slot = 0]: [number?]
) => ({ signalId, tag, slot, x, y });

describe("dropsFor", () => {
  it("drops once per area from the lit mark nearest the centre of its lit marks", () => {
    const drops = dropsFor(
      [
        at(1, "kafka", 0, 0),
        at(2, "kafka", 12, 24),
        at(3, "kafka", 3, 9),
        at(4, "rust", 500, 500),
      ],
      new Set([1, 2, 3]),
    );
    expect(drops).toEqual([{ x: 3, y: 9, delayMs: 0, slot: 0 }]);
  });
  /** A band curves, so the centre of its marks can lie in the gap beside it; a mark is always on it. */
  it("drops on a mark, never in the empty middle of a curved area", () => {
    const drops = dropsFor(
      [at(1, "kafka", 0, 0), at(2, "kafka", 100, 0), at(3, "kafka", 50, -40)],
      new Set([1, 2, 3]),
    );
    expect(drops.map(({ x, y }) => [x, y])).toEqual([[50, -40]]);
  });
  it("staggers areas, the one with the most matches first, ties by name", () => {
    const drops = dropsFor(
      [
        at(1, "rust", 0, 0, 2),
        at(2, "kafka", 100, 0, 1),
        at(3, "kafka", 120, 0, 1),
        at(4, "go", 300, 0, 3),
      ],
      new Set([1, 2, 3, 4]),
    );
    expect(drops.map((d) => [d.slot, d.delayMs])).toEqual([
      [1, 0],
      [3, ECHO.staggerMs],
      [2, 2 * ECHO.staggerMs],
    ]);
  });
  it("has no drop when nothing lit is drawn", () => {
    expect(dropsFor([at(1, "kafka", 0, 0)], new Set([9]))).toEqual([]);
  });
  it("drops from the biggest areas only, past the cap, and keeps every match lit", () => {
    const marks = Array.from({ length: MAX_DROPS + 6 }, (_, area) =>
      Array.from({ length: area + 1 }, (_, n) =>
        at(area * 100 + n, `area-${area}`, area, 0, area),
      ),
    ).flat();
    const drops = dropsFor(marks, new Set(marks.map((m) => m.signalId)));
    expect(drops).toHaveLength(MAX_DROPS);
    expect(drops[0]!.slot).toBe(MAX_DROPS + 5);
    expect(Math.min(...drops.map((d) => d.slot))).toBe(6);
  });
});
