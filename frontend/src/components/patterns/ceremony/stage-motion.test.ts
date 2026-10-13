import { describe, expect, it } from "vitest";

import { centredPlacement, settlePhase } from "./stage-motion";

const timing = { length: 3.4, settleSeconds: 0.85 };

describe("centredPlacement", () => {
  it("fits the card inside the viewport, centred", () => {
    // when
    const actual = centredPlacement(
      { width: 768, height: 400 },
      { width: 1280, height: 800 },
    );

    // then
    expect(actual.scale).toBeCloseTo(1232 / 768);
    expect(actual.x).toBeCloseTo(24);
    expect(actual.y).toBeCloseTo((800 - 400 * (1232 / 768)) / 2);
  });

  it("shrinks a card wider than a phone", () => {
    // when
    const actual = centredPlacement(
      { width: 380, height: 520 },
      { width: 412, height: 892 },
    );

    // then
    expect(actual.scale).toBeCloseTo(364 / 380);
  });
});

describe("settlePhase", () => {
  it("arrives on the target before the cross-fade begins", () => {
    // when
    const actual = settlePhase(3.4 + 0.6, timing);

    // then
    expect(actual.travel).toBeCloseTo(1);
    expect(actual.crossfade).toBeCloseTo(0);
  });

  it("cross-fades over the last quarter second of the settle", () => {
    // when
    const actual = [
      settlePhase(3.4 + 0.725, timing),
      settlePhase(3.4 + 0.85, timing),
    ];

    // then
    expect(actual[0]?.crossfade).toBeCloseTo(0.5);
    expect(actual[1]?.crossfade).toBe(1);
  });

  it("has fully settled by the end of the settle", () => {
    // when
    const actual = settlePhase(3.4 + 0.85, timing);

    // then
    expect(actual.settled).toBe(1);
  });

  it("keeps half a short settle for the journey", () => {
    // when
    const actual = settlePhase(1 + 0.2, { length: 1, settleSeconds: 0.4 });

    // then
    expect(actual.travel).toBe(1);
    expect(actual.crossfade).toBe(0);
  });
});
