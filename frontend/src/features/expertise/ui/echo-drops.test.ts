import { describe, expect, it } from "vitest";

import { dropsFor } from "@/features/expertise/ui/echo-drops";
import { ECHO } from "@/features/expertise/ui/echo-timeline";

const at = (
  signalId: number,
  tag: string,
  x: number,
  y: number,
  ...[slot = 0]: [number?]
) => ({ signalId, tag, slot, x, y });

describe("dropsFor", () => {
  it("drops once per area from the centre of its lit marks", () => {
    const drops = dropsFor(
      [at(1, "kafka", 0, 0), at(2, "kafka", 10, 20), at(3, "rust", 500, 500)],
      new Set([1, 2]),
    );
    expect(drops).toEqual([{ x: 5, y: 10, delayMs: 0, slot: 0 }]);
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
});
