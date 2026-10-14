import { describe, expect, it, vi } from "vitest";

import {
  blendOf,
  paintNow,
  type Tinted,
} from "@/features/expertise/ui/echo-blend";

function tinted(values: number[]): Tinted & { commits: number } {
  const t = {
    xy: new Float32Array([0, 0, 10, 0]),
    stride: 1,
    values: new Float32Array(values),
    targetFor: vi.fn(() => new Float32Array([1, 1])),
    commits: 0,
    commit: () => {
      t.commits += 1;
    },
  };
  return t;
}

describe("blendOf", () => {
  it("mixes from the values on screen when it was made, per item, by the amount at its position", () => {
    const t = tinted([0, 0.5]);
    const blend = blendOf(t, new Float32Array([1, 1]));
    t.values[0] = 0.9; // a later write must not move the blend's starting point
    blend.apply((x) => (x === 0 ? 0.5 : 0));
    expect([...t.values]).toEqual([0.5, 0.5]);
    expect(t.commits).toBe(1);
  });
  it("lands exactly on the target at 1", () => {
    const t = tinted([0.2, 0.3]);
    blendOf(t, new Float32Array([0.7, 0.1])).apply(() => 1);
    expect([...t.values].map((v) => +v.toFixed(5))).toEqual([0.7, 0.1]);
  });
});

describe("paintNow", () => {
  it("writes the target for a highlight and commits", () => {
    const t = tinted([0, 0]);
    paintNow(t, { tags: null, marks: null });
    expect([...t.values]).toEqual([1, 1]);
    expect(t.commits).toBe(1);
  });
});
