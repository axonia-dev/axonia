import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  buildHeightfield,
  heightAt,
} from "@/features/expertise/ui/terrain-heightfield";
import { SEA_LEVEL } from "@/features/expertise/ui/terrain-shading";

const size = { width: 400, height: 300 };

function mark(x: number, y: number, signalId = 1): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x,
    y,
    slot: 0,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: 0,
    reach: null,
  };
}

describe("buildHeightfield", () => {
  it("is open water everywhere when nothing has happened", () => {
    const field = buildHeightfield([], size);

    expect(Math.max(...field.values)).toBeLessThan(SEA_LEVEL);
  });

  it("lays the deepest water along the plane's border, where the clear colour and the polar caps take over", () => {
    const field = buildHeightfield([], size);
    const border = field.values.filter((_, cell) => {
      const col = cell % field.cols;
      const row = Math.floor(cell / field.cols);
      return (
        col === 0 ||
        row === 0 ||
        col === field.cols - 1 ||
        row === field.rows - 1
      );
    });

    expect(Math.max(...border)).toBe(0);
    expect(Math.max(...field.values)).toBeGreaterThan(0);
  });

  it("raises the ground under a contribution", () => {
    const field = buildHeightfield([mark(200, 150)], size);

    expect(heightAt(field, 200, 150)).toBeGreaterThan(0);
  });

  it("falls off with distance", () => {
    const field = buildHeightfield([mark(200, 150)], size);

    expect(heightAt(field, 200, 150)).toBeGreaterThan(
      heightAt(field, 240, 150),
    );
  });

  it("is higher where more contributions pile up", () => {
    const field = buildHeightfield(
      [
        ...Array.from({ length: 8 }, (_, i) => mark(200, 150, i + 1)),
        mark(340, 260, 99),
      ],
      size,
    );

    expect(heightAt(field, 200, 150)).toBeGreaterThan(
      heightAt(field, 340, 260),
    );
  });

  it("stands the same height however large the community is", () => {
    // Unbounded this grew without limit: at five thousand contributions the ground stood twenty times
    // higher than the globe it is wrapped on, and every mark projected outside the frame.
    const crowd = (count: number) =>
      buildHeightfield(
        Array.from({ length: count }, (_, i) => mark(200, 150, i + 1)),
        size,
      );

    expect(heightAt(crowd(4000), 200, 150)).toBeLessThanOrEqual(1);
    expect(
      heightAt(crowd(4000), 200, 150) - heightAt(crowd(10), 200, 150),
    ).toBeLessThan(0.02);
  });

  /**
   * The defect this replaced a peak-relative coastline to fix. Normalised against its own busiest spot, a field
   * says nothing about an area except how it compares to that spot, so one deep area drowns every other and the
   * map draws open water with a few faint stains on it.
   */
  it("leaves a modest area above water however much busier another one is", () => {
    const field = buildHeightfield(
      [
        mark(80, 80, 1),
        mark(84, 84, 2),
        ...Array.from({ length: 300 }, (_, i) => mark(320, 220, i + 10)),
      ],
      size,
    );

    expect(heightAt(field, 82, 82)).toBeGreaterThan(SEA_LEVEL);
  });

  it("raises no land far from any contribution", () => {
    const field = buildHeightfield([mark(20, 20)], size);

    expect(heightAt(field, 380, 280)).toBeLessThan(SEA_LEVEL);
  });

  it("samples between cells rather than stepping", () => {
    const field = buildHeightfield([mark(200, 150)], size);
    const a = heightAt(field, 210, 150);
    const b = heightAt(field, 211, 150);

    expect(a).not.toBe(b);
  });

  it("reads zero outside its own bounds rather than throwing", () => {
    const field = buildHeightfield([mark(200, 150)], size);

    expect(heightAt(field, -50, -50)).toBe(0);
    expect(heightAt(field, 5000, 5000)).toBe(0);
  });
});
