import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { buildStream } from "@/features/expertise/ui/stream-bins";
import {
  binsFor,
  MAX_BINS,
  MIN_BINS,
  PAD_X,
  PAD_Y,
  PX_PER_BIN,
  reseat,
  stack,
  xAt,
} from "@/features/expertise/ui/stream-layout";

const DAY = 86_400_000;

const size = { width: 800, height: 500 };

function mark(
  signalId: number,
  occurredAt: number,
  slot = 0,
  tag = "kafka",
): PlacedContribution {
  return {
    signalId,
    tag,
    x: 0,
    y: 0,
    slot,
    sourceType: "SLOT",
    userId: "u-1",
    occurredAt,
    reach: null,
  };
}

function layoutOf(marks: PlacedContribution[]) {
  const layout = buildStream(marks, binsFor(size.width));
  return { layout, stacked: stack(layout, size) };
}

describe("binsFor", () => {
  it("holds a narrow canvas above the bar-chart floor", () => {
    expect(binsFor(10)).toBe(MIN_BINS);
  });

  it("holds a very wide one under the noise ceiling", () => {
    expect(binsFor(100_000)).toBe(MAX_BINS);
  });

  it("gives one bin per slice of width in between", () => {
    expect(binsFor(600)).toBe(600 / PX_PER_BIN);
  });
});

describe("xAt", () => {
  it("puts the middle of the history on the origin", () => {
    expect(xAt(0.5, size)).toBe(0);
  });

  /** Bled to the very edges the shape has no beginning and no end, and reads as a texture rather than a history. */
  it("insets the ends so the history starts and stops inside the box", () => {
    expect(xAt(0, size)).toBe(-size.width / 2 + PAD_X);
    expect(xAt(1, size)).toBe(size.width / 2 - PAD_X);
  });
});

describe("stack", () => {
  it("gives every topic its own band, carrying its neighbourhood's colour", () => {
    const { stacked } = layoutOf([
      mark(1, 0, 0, "kafka"),
      mark(2, DAY, 1, "rust"),
    ]);

    expect(stacked.map((band) => band.tag)).toEqual(["kafka", "rust"]);
    expect(stacked.map((band) => band.slot)).toEqual([0, 1]);
  });

  it("centres every bin on zero rather than resting the stack on a floor", () => {
    const { stacked } = layoutOf([
      mark(1, 0, 0, "kafka"),
      mark(2, DAY, 1, "rust"),
    ]);
    const bins = stacked[0]!.lower.length;
    const spans = Array.from(
      { length: bins },
      (_, bin) => stacked[0]!.lower[bin]! + stacked.at(-1)!.upper[bin]!,
    );

    expect(Math.max(...spans.map(Math.abs))).toBeLessThan(1e-9);
  });

  it("leaves the padding clear at the thickest bin", () => {
    const { stacked } = layoutOf([
      mark(1, 0),
      mark(2, 5 * DAY),
      mark(3, 5 * DAY),
    ]);
    const tops = stacked.flatMap((band) => band.upper);

    expect(Math.max(...tops)).toBeLessThanOrEqual(size.height / 2 - PAD_Y);
  });

  it("stacks a second band on top of the first rather than through it", () => {
    const { stacked } = layoutOf([
      mark(1, 0, 0, "kafka"),
      mark(2, 0, 1, "rust"),
    ]);

    for (const [bin, lower] of stacked[1]!.lower.entries()) {
      expect(lower).toBeCloseTo(stacked[0]!.upper[bin]!, 9);
    }
  });

  it("draws a flat nothing where there is no history at all", () => {
    const { stacked } = layoutOf([]);

    expect(stacked).toEqual([]);
  });
});

describe("reseat", () => {
  const marks = [mark(1, 0), mark(2, 100 * DAY)];

  it("runs time along the horizontal axis, oldest first", () => {
    const { layout, stacked } = layoutOf(marks);

    const actual = reseat(marks, layout, stacked, size);

    expect(actual[0]!.x).toBeLessThan(actual[1]!.x);
    expect(actual[0]!.x).toBeCloseTo(-size.width / 2 + PAD_X, 9);
    expect(actual[1]!.x).toBeCloseTo(size.width / 2 - PAD_X, 9);
  });

  it("seats a mark inside its own band, clear of both its edges", () => {
    const { layout, stacked } = layoutOf(marks);
    const bins = stacked[0]!.lower.length;
    const band = stacked[0]!;

    const actual = reseat(marks, layout, stacked, size);

    for (const [i, bin] of [0, bins - 1].entries()) {
      const thickness = band.upper[bin]! - band.lower[bin]!;

      expect(actual[i]!.y).toBeGreaterThan(band.lower[bin]! + thickness * 0.15);
      expect(actual[i]!.y).toBeLessThan(band.upper[bin]! - thickness * 0.15);
    }
  });

  it("scatters marks across the band rather than piling them on its middle", () => {
    // A community with one neighbourhood has one band, and every mark it has seated on that band's centre line
    // draws as a solid bar rather than as contributions.
    const crowd = Array.from({ length: 40 }, (_, i) =>
      mark(i + 1, Date.UTC(2026, 0, 1 + (i % 4))),
    );
    const { layout, stacked } = layoutOf(crowd);

    const seats = new Set(reseat(crowd, layout, stacked, size).map((m) => m.y));

    expect(seats.size).toBeGreaterThan(30);
  });

  it("seats a mark in the same place every time, so a reload does not move it", () => {
    const { layout, stacked } = layoutOf(marks);

    expect(reseat(marks, layout, stacked, size).map((m) => m.y)).toEqual(
      reseat(marks, layout, stacked, size).map((m) => m.y),
    );
  });

  it("drops a mark whose date could not be read", () => {
    const dated = [...marks, mark(3, Number.NaN)];
    const { layout, stacked } = layoutOf(dated);

    const actual = reseat(dated, layout, stacked, size);

    expect(actual.map((m) => m.signalId)).toEqual([1, 2]);
  });

  it("drops a mark whose topic has no band", () => {
    const { layout, stacked } = layoutOf(marks);

    const actual = reseat(
      [...marks, mark(4, DAY, 7, "rust")],
      layout,
      stacked,
      size,
    );

    expect(actual.map((m) => m.signalId)).toEqual([1, 2]);
  });

  it("keeps everything else about the contribution it re-seats", () => {
    const { layout, stacked } = layoutOf(marks);

    const actual = reseat(marks, layout, stacked, size)[0]!;

    expect(actual.signalId).toBe(1);
    expect(actual.tag).toBe("kafka");
    expect(actual.sourceType).toBe("SLOT");
  });
});
