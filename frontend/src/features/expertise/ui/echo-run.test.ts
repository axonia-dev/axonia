import { describe, expect, it } from "vitest";

import { runFor } from "@/features/expertise/ui/echo-run";

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

describe("runFor", () => {
  it("steps every frame and ends with one step at its full length", () => {
    const clock = fakeClock();
    const seen: number[] = [];
    runFor({ clock, lengthMs: 100, step: (ms) => seen.push(ms) });
    clock.tick(40);
    clock.tick(40);
    clock.tick(40);
    expect(seen).toEqual([0, 40, 80, 100]);
    expect(clock.pending()).toBe(0);
  });
  it("stops stepping once cancelled", () => {
    const clock = fakeClock();
    const seen: number[] = [];
    const cancel = runFor({
      clock,
      lengthMs: 100,
      step: (ms) => seen.push(ms),
    });
    cancel();
    clock.tick(40);
    expect(seen).toEqual([0]);
  });
});
