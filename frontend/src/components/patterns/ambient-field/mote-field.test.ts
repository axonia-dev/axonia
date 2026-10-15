import { describe, expect, it } from "vitest";

import {
  buildMotes,
  type Light,
  moteAt,
  seedOf,
} from "@/components/patterns/ambient-field/mote-field";

const BOX = { width: 720, height: 480 };

let keys = 0;

function light(overrides: Partial<Light> = {}): Light {
  keys += 1;
  return {
    key: `act-${keys}`,
    position: 0.5,
    freshness: 0.5,
    size: "medium",
    blooms: false,
    ...overrides,
  };
}

describe("buildMotes", () => {
  it("makes one mote per light plus the ambient dust", () => {
    const actual = buildMotes({
      lights: [light(), light()],
      ambient: 22,
      seed: 1,
    });
    expect(actual).toHaveLength(24);
  });

  it("is the same sky for the same seed", () => {
    const field = {
      lights: [light(), light({ position: 0.9 })],
      ambient: 5,
      seed: 7,
    };
    const first = buildMotes(field).map((mote) => moteAt(mote, 3, BOX));
    const second = buildMotes(field).map((mote) => moteAt(mote, 3, BOX));
    expect(second).toEqual(first);
  });

  it("is a different sky for a different seed", () => {
    const lights = [light()];
    const first = moteAt(
      buildMotes({ lights, ambient: 0, seed: 1 })[0]!,
      0,
      BOX,
    );
    const second = moteAt(
      buildMotes({ lights, ambient: 0, seed: 2 })[0]!,
      0,
      BOX,
    );
    expect(second.y).not.toEqual(first.y);
  });

  it("dormant is dust only", () => {
    expect(buildMotes({ lights: [], ambient: 22, seed: 1 })).toHaveLength(22);
  });
});

describe("moteAt", () => {
  it("places a light at its date: today on the right, the window's start on the left", () => {
    const [today, old] = buildMotes({
      lights: [light({ position: 0.99 }), light({ position: 0.01 })],
      ambient: 0,
      seed: 3,
    });
    expect(moteAt(today!, 0, BOX).x).toBeGreaterThan(BOX.width * 0.8);
    expect(moteAt(old!, 0, BOX).x).toBeLessThan(BOX.width * 0.2);
  });

  it("keeps every mote inside the card", () => {
    const motes = buildMotes({
      lights: [light({ position: 0 }), light({ position: 1 })],
      ambient: 22,
      seed: 5,
    });
    for (const seconds of [0, 1.5, 30, 600]) {
      for (const mote of motes) {
        const sample = moteAt(mote, seconds, BOX);
        expect(sample.x).toBeGreaterThanOrEqual(0);
        expect(sample.x).toBeLessThanOrEqual(BOX.width);
        expect(sample.y).toBeGreaterThanOrEqual(0);
        expect(sample.y).toBeLessThan(BOX.height);
      }
    }
  });

  it("draws a fresher act brighter", () => {
    const [fresh, stale] = buildMotes({
      lights: [light({ freshness: 1 }), light({ freshness: 0 })],
      ambient: 0,
      seed: 9,
    });
    expect(moteAt(fresh!, 0, BOX).alpha).toBeGreaterThan(
      moteAt(stale!, 0, BOX).alpha,
    );
  });

  it("draws a session larger than an addition, and an addition larger than a join", () => {
    const [large, medium, small] = buildMotes({
      lights: [
        light({ size: "large" }),
        light({ size: "medium" }),
        light({ size: "small" }),
      ],
      ambient: 0,
      seed: 4,
    });
    expect(moteAt(large!, 0, BOX).radius).toBeGreaterThan(
      moteAt(medium!, 0, BOX).radius,
    );
    expect(moteAt(medium!, 0, BOX).radius).toBeGreaterThan(
      moteAt(small!, 0, BOX).radius,
    );
  });

  it("rises over time", () => {
    const [mote] = buildMotes({ lights: [light()], ambient: 0, seed: 11 });
    const start = moteAt(mote!, 0, BOX).y;
    const later = moteAt(mote!, 0.5, BOX).y;
    expect(later).toBeLessThan(start);
  });

  it("blooms only for a light that blooms", () => {
    const [plain, blooming] = buildMotes({
      lights: [light(), light({ blooms: true })],
      ambient: 0,
      seed: 6,
    });
    const radii = (mote: typeof plain) =>
      Array.from(
        { length: 200 },
        (_, step) => moteAt(mote!, step * 0.1, BOX).radius,
      );
    expect(new Set(radii(plain)).size).toBe(1);
    expect(Math.max(...radii(blooming))).toBeGreaterThan(
      Math.min(...radii(blooming)) * 1.5,
    );
  });

  it("spreads a burst of same-day acts across the card instead of one column", () => {
    for (const seed of [1, 8, 42, 1234, 99_999]) {
      const motes = buildMotes({
        lights: Array.from({ length: 30 }, (_, ordinal) =>
          light({ key: `join:0:${ordinal}`, position: 0.994 }),
        ),
        ambient: 0,
        seed,
      });
      const columns = new Map<number, number>();
      for (const mote of motes) {
        expect(mote.x).toBeGreaterThanOrEqual(0.02);
        expect(mote.x).toBeLessThanOrEqual(0.98);
        const column = Math.floor(mote.x * 100);
        columns.set(column, (columns.get(column) ?? 0) + 1);
      }
      expect(Math.max(...columns.values())).toBeLessThanOrEqual(6);
    }
  });
});

describe("a changing sky", () => {
  const sky = (lights: readonly Light[]) =>
    buildMotes({ lights, ambient: 22, seed: 13 });

  it("leaves every mote in place when one act joins", () => {
    const before = [light(), light({ position: 0.9 }), light()];
    const after = [light({ position: 0.99 }), ...before];

    const [, ...kept] = sky(after);

    expect(kept).toEqual(sky(before));
  });

  it("leaves the dust in place however many acts there are", () => {
    const dustOf = (lights: readonly Light[]) =>
      sky(lights).slice(lights.length);
    expect(dustOf([light(), light()])).toEqual(dustOf([]));
  });
});

describe("seedOf", () => {
  it("is stable per text and differs between texts", () => {
    expect(seedOf("backend-guild")).toBe(seedOf("backend-guild"));
    expect(seedOf("backend-guild")).not.toBe(seedOf("frontend-guild"));
  });
});
