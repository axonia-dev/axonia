import { describe, expect, it } from "vitest";

import { echoField } from "@/features/expertise/ui/echo-field";
import { amountAt, ECHO, echoFor } from "@/features/expertise/ui/echo-timeline";

const echo = echoFor(
  [
    { x: 10, y: 20, delayMs: 0, slot: 0 },
    { x: 300, y: 180, delayMs: ECHO.staggerMs, slot: 1 },
  ],
  { minX: 0, minY: 0, maxX: 400, maxY: 200 },
);
const points = [
  [0, 0],
  [10, 20],
  [150, 90],
  [400, 200],
  [290, 10],
];
const xy = new Float32Array(points.flat());

describe("echoField", () => {
  it("carries every point as far as the echo's own rule does, at every moment", () => {
    const field = echoField(echo, xy);
    for (const ms of [0, 200, 900, 2000, 3500, 4600]) {
      field.at(ms);
      points.forEach(([x, y], item) =>
        expect(field.amounts[item]).toBeCloseTo(amountAt(echo, x!, y!, ms), 12),
      );
    }
  });

  it("states a finished echo as exactly 1 everywhere", () => {
    const field = echoField(echo, xy);
    field.at(ECHO.staggerMs + ECHO.durationMs);
    expect([...field.amounts]).toEqual(points.map(() => 1));
  });

  /** A frame of an echo is seconds of frames: none of them may allocate. */
  it("writes every frame into the same amounts", () => {
    const field = echoField(echo, xy);
    const { amounts } = field;
    field.at(500);
    field.at(1500);
    expect(field.amounts).toBe(amounts);
  });
});
