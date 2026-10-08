import { expect, it } from "vitest";

import { drawnExtent } from "@/features/expertise/ui/topic-graph-framing";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

const SIZE = { width: 800, height: 500 };

function topic(overrides: Partial<PlacedTopic> = {}): PlacedTopic {
  return {
    tag: "kafka",
    x: 400,
    y: 250,
    r: 20,
    declarations: 0,
    practised: true,
    ...overrides,
  };
}

it("holds every area, and keeps water around them", () => {
  const extent = drawnExtent(
    [topic({ x: 300, y: 200 }), topic({ tag: "rust", x: 500, y: 300 })],
    SIZE,
  );

  expect(extent.minX).toBeLessThan(300 - 20);
  expect(extent.maxX).toBeGreaterThan(500 + 20);
  expect(extent.minY).toBeLessThan(200 - 20);
  expect(extent.maxY).toBeGreaterThan(300 + 20);
});

/** The ring sits outside the circle, and a map cropped to the circle cuts it in half. */
it("measures an area by its outermost mark, not by its circle", () => {
  const plain = drawnExtent([topic()], SIZE);
  const ringed = drawnExtent([topic({ declarations: 4 })], SIZE);

  expect(ringed.maxX).toBeGreaterThan(plain.maxX);
});

it("is the whole plane when there is nothing to measure", () => {
  expect(drawnExtent([], SIZE)).toStrictEqual({
    minX: 0,
    maxX: SIZE.width,
    minY: 0,
    maxY: SIZE.height,
  });
});
