import { describe, expect, it } from "vitest";

import type { MapLabel } from "@/features/expertise/ui/map-labels";
import { placeLabels } from "@/features/expertise/ui/map-labels-placement";
import type { Spot } from "@/features/expertise/ui/terrain-projection";

const size = { width: 800, height: 600 };

function straight(x: number, y: number): Spot {
  return { left: x / size.width, top: y / size.height };
}

function headline(text: string, kind: MapLabel["kind"]): MapLabel {
  return {
    key: `${kind}:kafka`,
    text,
    kind,
    weight: 1,
    at: [{ x: 0, y: 0, tags: ["kafka"] }],
  };
}

/**
 * jsdom measures every box as zero, so the room a headline claims is only visible through where it fits. A missing
 * entry for the kind is `undefined` arithmetic: every box NaN, and every such headline silently dropped. The figures
 * are the boxes `MapLabelLayer` draws, measured in a browser: a named area's plate is 22px tall with 9px of padding
 * and rule either side of its text, a neighbourhood's capitals a bare 16px line.
 */
describe.each([
  { kind: "named", text: "Streaming", halfWidth: 9 * 3.25 + 9, halfHeight: 11 },
  {
    kind: "neighbourhood",
    text: "AROUND KAFKA",
    halfWidth: 12 * 4.2,
    halfHeight: 8,
  },
] as const)(
  "the room a $kind headline claims",
  ({ kind, text, halfWidth, halfHeight }) => {
    const label: MapLabel = {
      key: `${kind}:kafka`,
      text,
      kind,
      weight: 1,
      at: [{ x: 0, y: 0, tags: ["kafka"] }],
    };

    function fitsAt(x: number, y: number): boolean {
      return placeLabels([label], () => straight(x, y), size).length === 1;
    }

    // Plus a 4px gutter either way.
    it("claims its measured width across", () => {
      expect(fitsAt(halfWidth + 4.01, 300)).toBe(true);
      expect(fitsAt(halfWidth + 3.99, 300)).toBe(false);
    });

    // No lift: the lift is for an area's name over its own marks.
    it("claims its measured height, standing on its seat", () => {
      expect(fitsAt(400, halfHeight + 4.01)).toBe(true);
      expect(fitsAt(400, halfHeight + 3.99)).toBe(false);
    });
  },
);

/**
 * "DDD & CQRS" is 10 characters on a plate: 32.5px of text plus 9px of plate either side and the 4px gutter, so its
 * box runs 45.5px either side of its seat and 15px above and below.
 */
describe("a name on the globe", () => {
  const disc = { x: 400, y: 300, r: 100 };
  const label = headline("DDD & CQRS", "named");

  function fitsAt(x: number, y: number): boolean {
    return placeLabels([label], () => straight(x, y), size, disc).length === 1;
  }

  it("stands on the planet when its whole box is inside the outline", () => {
    expect(fitsAt(400, 300)).toBe(true);
  });

  // Right of centre by r minus half the box: the far edge touches the outline at the box's middle, but its
  // corners, 15px up and down, are already past it.
  it("is dropped when a corner of its box hangs past the outline", () => {
    expect(fitsAt(400 + 100 - 45.5, 300)).toBe(false);
  });

  it("fits where every corner is inside, a little further in", () => {
    const inset = Math.sqrt(100 ** 2 - 15 ** 2) - 45.5;
    expect(fitsAt(400 + inset - 0.01, 300)).toBe(true);
    expect(fitsAt(400 + inset + 0.01, 300)).toBe(false);
  });

  it("is not held to an outline the flat reading does not have", () => {
    expect(placeLabels([label], () => straight(740, 300), size)).toHaveLength(
      1,
    );
  });
});
