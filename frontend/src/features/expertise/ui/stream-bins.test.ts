import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { buildStream } from "@/features/expertise/ui/stream-bins";

const DAY = 86_400_000;

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
    userId: "u1",
    occurredAt,
    reach: null,
  };
}

describe("buildStream", () => {
  it("spans the first contribution to the last", () => {
    const layout = buildStream([mark(1, 0), mark(2, 10 * DAY)], 10);

    expect(layout.from).toBe(0);
    expect(layout.to).toBe(10 * DAY);
  });

  /**
   * A band per neighbourhood drew three copies of one silhouette, because the dates a neighbourhood is busy are
   * the dates the whole community is busy. A band per topic is what makes the stream a picture of a practice.
   */
  it("gives every topic its own band, carrying its neighbourhood's colour", () => {
    const layout = buildStream(
      [mark(1, 0, 0, "kafka"), mark(2, DAY, 2, "rust")],
      4,
    );

    expect(layout.bands.map((b) => b.tag)).toEqual(["kafka", "rust"]);
    expect(layout.bands.map((b) => b.slot)).toEqual([0, 2]);
  });

  it("keeps two topics of one neighbourhood apart rather than summing them", () => {
    const layout = buildStream(
      [mark(1, 0, 0, "kafka"), mark(2, DAY, 0, "grpc")],
      4,
    );

    expect(layout.bands).toHaveLength(2);
  });

  /** Neighbours stay together so their shared colour reads as one region, and the busiest topic takes the middle
   *  of the stack, where a band is thickest. */
  it("orders the bands by neighbourhood, then by how much each topic carries", () => {
    const layout = buildStream(
      [
        mark(1, 0, 1, "zig"),
        mark(2, DAY, 0, "grpc"),
        mark(3, DAY, 0, "kafka"),
        mark(4, 2 * DAY, 0, "kafka"),
      ],
      4,
    );

    expect(layout.bands.map((b) => b.tag)).toEqual(["kafka", "grpc", "zig"]);
  });

  it("draws the same picture from the same history on every load", () => {
    const marks = [mark(1, 0, 0, "kafka"), mark(2, DAY, 0, "grpc")];

    expect(buildStream(marks, 4).bands.map((b) => b.tag)).toEqual(
      buildStream([...marks].reverse(), 4).bands.map((b) => b.tag),
    );
  });

  it("gives each band one value per bin", () => {
    const layout = buildStream([mark(1, 0), mark(2, 10 * DAY)], 7);

    expect(layout.bands[0]?.values).toHaveLength(7);
  });

  it("puts weight where the contributions are", () => {
    const early = Array.from({ length: 10 }, (_, i) => mark(i + 1, i * 1000));
    const layout = buildStream([...early, mark(99, 100 * DAY)], 10);
    const values = layout.bands[0]!.values;

    expect(values[0]!).toBeGreaterThan(values[5]!);
  });

  it("smooths, so a single contribution is a swell and not a spike", () => {
    const layout = buildStream([mark(1, 5 * DAY)], 11);
    const values = layout.bands[0]!.values;
    const nonZero = values.filter((v) => v > 0).length;

    expect(nonZero).toBeGreaterThan(1);
  });

  it("handles a community whose contributions all landed the same day", () => {
    const layout = buildStream([mark(1, DAY), mark(2, DAY)], 5);

    expect(layout.bands[0]?.values).toHaveLength(5);
    expect(layout.bands[0]?.values.some((v) => v > 0)).toBe(true);
  });

  it("is empty for no contributions", () => {
    expect(buildStream([], 10).bands).toEqual([]);
  });

  it("refuses a bin count it cannot bin into", () => {
    expect(() => buildStream([mark(1, 0)], 0)).toThrow(RangeError);
  });

  it("drops a contribution whose date could not be read instead of collapsing every band", () => {
    const layout = buildStream(
      [mark(1, Number.NaN), mark(2, 0), mark(3, 10 * DAY)],
      10,
    );

    expect(layout.from).toBe(0);
    expect(layout.to).toBe(10 * DAY);
    expect(layout.bands[0]!.values.every(Number.isFinite)).toBe(true);
  });
});
