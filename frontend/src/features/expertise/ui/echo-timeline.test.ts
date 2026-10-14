import { describe, expect, it } from "vitest";

import {
  amountAt,
  ECHO,
  echoFor,
  echoLength,
  fadeAmount,
  reachedAt,
  ringsAt,
} from "@/features/expertise/ui/echo-timeline";

const box = { minX: 0, minY: 0, maxX: 800, maxY: 600 };
const one = echoFor([{ x: 100, y: 100, delayMs: 0, slot: 0 }], box);

describe("echoFor", () => {
  it("reaches past the farthest corner by its own soft front, so every point ends fully taken", () => {
    const wave = one.waves[0]!;
    const corner = Math.hypot(700, 500);
    expect(wave.feather).toBeCloseTo(corner * ECHO.featherShare);
    expect(wave.reach).toBeCloseTo(corner + wave.feather);
    expect(one.local).toBe(false);
  });
  it("is local once there is more than one drop", () => {
    expect(
      echoFor(
        [
          { x: 0, y: 0, delayMs: 0, slot: 0 },
          { x: 9, y: 9, delayMs: 275, slot: 1 },
        ],
        box,
      ).local,
    ).toBe(true);
  });
});

describe("amountAt", () => {
  it("is 0 everywhere before the echo starts and 1 everywhere once it ends", () => {
    expect(amountAt(one, 100, 100, 0)).toBe(0);
    expect(amountAt(one, 800, 600, ECHO.durationMs)).toBe(1);
    expect(amountAt(one, 100, 100, ECHO.durationMs)).toBe(1);
  });
  it("takes a near point before a far one", () => {
    const at = ECHO.durationMs * 0.3;
    expect(amountAt(one, 120, 100, at)).toBeGreaterThan(
      amountAt(one, 700, 500, at),
    );
  });
  it("waits for a drop's own delay", () => {
    const late = echoFor([{ x: 100, y: 100, delayMs: 500, slot: 0 }], box);
    expect(amountAt(late, 100, 100, 400)).toBe(0);
  });
  it("is taken by whichever drop reaches it first", () => {
    const two = echoFor(
      [
        { x: 0, y: 0, delayMs: 0, slot: 0 },
        { x: 800, y: 600, delayMs: 0, slot: 1 },
      ],
      box,
    );
    expect(amountAt(two, 790, 590, 300)).toBeGreaterThan(0);
  });
});

describe("reachedAt", () => {
  it("is the drop's delay at its own centre and grows with distance", () => {
    const late = echoFor([{ x: 100, y: 100, delayMs: 500, slot: 0 }], box);
    expect(reachedAt(late, 100, 100)).toBe(500);
    expect(reachedAt(late, 600, 100)).toBeGreaterThan(
      reachedAt(late, 300, 100),
    );
    expect(reachedAt(late, 800, 600)).toBeLessThanOrEqual(
      500 + ECHO.durationMs,
    );
  });
});

describe("ringsAt", () => {
  it("draws three rings a lag apart, fading as they travel", () => {
    const rings = ringsAt(one, 1000);
    expect(rings).toHaveLength(3);
    expect(rings[0]!.radius).toBeGreaterThan(rings[1]!.radius);
    expect(rings[0]!.alpha).toBeLessThan(ECHO.ringPeakAlpha);
  });
  it("draws nothing before a ring leaves or after it ends", () => {
    expect(ringsAt(one, 0)).toEqual([]);
    expect(ringsAt(one, echoLength(one))).toEqual([]);
  });
  it("fades a local ring out within the local radius", () => {
    const local = echoFor(
      [
        { x: 100, y: 100, delayMs: 0, slot: 0 },
        { x: 700, y: 500, delayMs: 0, slot: 1 },
      ],
      box,
    );
    for (const ring of ringsAt(local, ECHO.durationMs * 0.8))
      expect(ring.radius < ECHO.localRingPx || ring.alpha === 0).toBe(true);
  });
});

describe("echoLength and fadeAmount", () => {
  it("lasts until the last drop's twinkles end", () => {
    const late = echoFor([{ x: 0, y: 0, delayMs: 550, slot: 0 }], box);
    expect(echoLength(late)).toBe(550 + ECHO.durationMs + ECHO.twinkleMs);
  });
  it("fades from 0 to 1 over the fade", () => {
    expect(fadeAmount(0)).toBe(0);
    expect(fadeAmount(ECHO.fadeMs)).toBe(1);
  });
});
