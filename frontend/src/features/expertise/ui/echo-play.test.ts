import { describe, expect, it } from "vitest";

import type { Tinted } from "@/features/expertise/ui/echo-blend";
import { type EchoStage, play } from "@/features/expertise/ui/echo-play";
import type { TwinklePoint } from "@/features/expertise/ui/echo-twinkles";
import { ECHO, type Ring } from "@/features/expertise/ui/echo-timeline";
import type {
  DrawnHighlight,
  MapTransition,
} from "@/features/expertise/ui/echo-transition";

function fakeClock() {
  let now = 0;
  const queue = new Map<number, () => void>();
  let next = 1;
  return {
    now: () => now,
    request: (cb: () => void) => {
      queue.set(next, cb);
      return next++;
    },
    cancel: (handle: number) => {
      queue.delete(handle);
    },
    tick(ms: number) {
      now += ms;
      const due = [...queue.values()];
      queue.clear();
      due.forEach((cb) => cb());
    },
    pending: () => queue.size,
  };
}

function tinted(): Tinted {
  return {
    xy: new Float32Array([0, 0, 90, 90]),
    stride: 1,
    values: new Float32Array([0, 0]),
    targetFor: () => new Float32Array([1, 1]),
    commit: () => {},
  };
}

function fakeStage(tint: Tinted) {
  const shown: Ring[][] = [];
  const twinkles = {
    points: [] as TwinklePoint[],
    at: [] as number[],
    disposed: 0,
  };
  let requests = 0;
  const stage: EchoStage = {
    tints: [tint],
    marks: [
      { signalId: 1, tag: "kafka", slot: 0, x: 10, y: 10 },
      { signalId: 2, tag: "rust", slot: 1, x: 80, y: 80 },
    ],
    bounds: { minX: 0, minY: 0, maxX: 100, maxY: 100 },
    rings: {
      objects: [],
      show: (rings) => shown.push([...rings]),
      dispose: () => {},
    },
    twinkles: (points) => {
      twinkles.points = [...points];
      return {
        object: null as never,
        at: (ms) => twinkles.at.push(ms),
        resize: () => {},
        dispose: () => {
          twinkles.disposed += 1;
        },
      };
    },
    request: () => {
      requests += 1;
    },
  };
  return { stage, shown, twinkles, requests: () => requests };
}

const drawnOf = (
  transition: MapTransition,
  lit: number[] = [1, 2],
): DrawnHighlight => ({
  highlight: { tags: new Set(["kafka", "rust"]), marks: new Set(lit) },
  transition,
});

const ringsShown = (shown: Ring[][]) => shown.some((rings) => rings.length > 0);
const echoMs = ECHO.staggerMs + ECHO.durationMs + ECHO.twinkleMs;

describe("play", () => {
  it("paints a snap at once and asks for one frame only", () => {
    const tint = tinted();
    const clock = fakeClock();
    const { stage, requests } = fakeStage(tint);
    play({ stage, drawn: drawnOf("snap"), clock });
    expect([...tint.values]).toEqual([1, 1]);
    expect(requests()).toBe(1);
    expect(clock.pending()).toBe(0);
  });

  it("fades to the target without a ring", () => {
    const tint = tinted();
    const clock = fakeClock();
    const { stage, shown } = fakeStage(tint);
    play({ stage, drawn: drawnOf("fade"), clock });
    clock.tick(ECHO.fadeMs / 2);
    expect(tint.values[0]).toBeGreaterThan(0);
    expect(tint.values[0]).toBeLessThan(1);
    clock.tick(ECHO.fadeMs);
    expect([...tint.values]).toEqual([1, 1]);
    expect(ringsShown(shown)).toBe(false);
    expect(clock.pending()).toBe(0);
  });

  it("echoes from the lit areas and ends on exactly the target, its rings and twinkles gone", () => {
    const tint = tinted();
    const clock = fakeClock();
    const { stage, shown, twinkles, requests } = fakeStage(tint);
    play({ stage, drawn: drawnOf("echo"), clock });
    clock.tick(500);
    expect(shown.at(-1)!.length).toBeGreaterThan(0);
    expect(tint.values[0]).toBeGreaterThan(tint.values[1]!);
    expect(twinkles.points).toHaveLength(2);
    expect(twinkles.at.at(-1)).toBe(500);

    clock.tick(echoMs);
    expect([...tint.values]).toEqual([1, 1]);
    expect(shown.at(-1)).toEqual([]);
    expect(twinkles.disposed).toBe(1);
    expect(clock.pending()).toBe(0);
    expect(requests()).toBe(3);
  });

  it("fades an echo that has nowhere to start from", () => {
    const tint = tinted();
    const clock = fakeClock();
    const { stage, shown, twinkles } = fakeStage(tint);
    play({ stage, drawn: drawnOf("echo", [99]), clock });
    clock.tick(ECHO.fadeMs);
    expect([...tint.values]).toEqual([1, 1]);
    expect(ringsShown(shown)).toBe(false);
    expect(twinkles.points).toHaveLength(0);
    expect(clock.pending()).toBe(0);
  });

  it("stops at once when cancelled, taking its rings and twinkles with it", () => {
    const tint = tinted();
    const clock = fakeClock();
    const { stage, shown, twinkles } = fakeStage(tint);
    const cancel = play({ stage, drawn: drawnOf("echo"), clock });
    clock.tick(500);
    cancel();
    const frozen = [...tint.values];
    clock.tick(500);
    expect([...tint.values]).toEqual(frozen);
    expect(shown.at(-1)).toEqual([]);
    expect(twinkles.disposed).toBe(1);
  });

  it("carries a term refined mid-echo on from where the first one got to", () => {
    const tint = tinted();
    const clock = fakeClock();
    const { stage } = fakeStage(tint);
    const cancel = play({ stage, drawn: drawnOf("echo"), clock });
    clock.tick(1500);
    const reached = [...tint.values];
    expect(reached[0]).toBeGreaterThan(0);
    cancel();

    play({ stage, drawn: drawnOf("echo", [2]), clock });
    let lowest = [...tint.values];
    for (let at = 0; at < echoMs; at += 100) {
      clock.tick(100);
      lowest = lowest.map((value, i) => Math.min(value, tint.values[i]!));
    }
    expect(lowest[0]).toBeGreaterThanOrEqual(reached[0]!);
    expect(lowest[1]).toBeGreaterThanOrEqual(reached[1]!);
    expect([...tint.values]).toEqual([1, 1]);
  });
});
