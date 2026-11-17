import { describe, expect, it } from "vitest";

import { topicOf } from "@/features/mentoring/test-data";
import {
  footprints,
  MAX_STOPS,
  type Point,
  standingIndex,
  standingOfStops,
  STOP_SPACING,
  stripLayout,
  trailLayout,
  trailOrder,
} from "@/features/mentoring/ui/trail/trail-layout";

const todo = topicOf({ id: 1, state: "TODO", position: 0 });
const doing = topicOf({ id: 2, state: "IN_PROGRESS", position: 0 });
const doingToo = topicOf({ id: 3, state: "IN_PROGRESS", position: 1 });
const doneFirst = topicOf({ id: 4, state: "DONE", position: 0 });
const doneLast = topicOf({ id: 5, state: "DONE", position: 1 });

function closest(points: Point[]): number {
  let nearest = Number.POSITIVE_INFINITY;
  points.forEach((a, i) =>
    points.slice(i + 1).forEach((b) => {
      nearest = Math.min(nearest, Math.hypot(a.x - b.x, a.y - b.y));
    }),
  );
  return nearest;
}

describe("trailOrder", () => {
  it("orders Done first, then In progress, then To do", () => {
    // when
    const actual = trailOrder([todo, doingToo, doneLast, doing, doneFirst]);

    // then
    expect(actual.map((topic) => topic.id)).toEqual([4, 5, 2, 3, 1]);
  });
});

describe("standingIndex", () => {
  it("stands at the first In progress stop, else the last Done", () => {
    // when
    const actual = [
      standingIndex([todo, doing, doneFirst, doneLast]),
      standingIndex([todo, doneFirst, doneLast]),
      standingIndex([todo]),
    ];

    // then
    expect(actual).toEqual([2, 1, 0]);
  });
});

describe("trailLayout", () => {
  it.each(["horizontal", "vertical"] as const)(
    "lays out 30 stops without two closer than a stop's size, %s",
    (orientation) => {
      // when
      const actual = trailLayout(MAX_STOPS, orientation);

      // then
      expect(actual.stops).toHaveLength(MAX_STOPS);
      expect(closest(actual.stops)).toBeGreaterThanOrEqual(STOP_SPACING);
      expect(
        actual.stops.every(
          (stop) =>
            stop.x > 0 && stop.x < actual.width && stop.y < actual.height,
        ),
      ).toBe(true);
    },
  );

  it("lays out the same path every time and never more than thirty stops", () => {
    // when
    const actual = trailLayout(45, "horizontal");

    // then
    expect(actual.stops).toHaveLength(MAX_STOPS);
    expect(actual.path).toBe(trailLayout(30, "horizontal").path);
  });

  it("winds through every stop from the trailhead", () => {
    // when
    const actual = trailLayout(3, "vertical");

    // then
    expect(
      actual.path.startsWith(`M ${actual.start.x} ${actual.start.y}`),
    ).toBe(true);
    expect(actual.legs.map((leg) => leg.to)).toEqual(actual.stops);
  });
});

describe("footprints", () => {
  it("leaves one print per note along the walked legs, none when nothing was walked", () => {
    // given
    const { legs } = trailLayout(4, "horizontal");

    // when
    const actual = [footprints(legs.slice(0, 2), 5), footprints([], 5)];

    // then
    expect(actual[0]).toHaveLength(5);
    expect(actual[1]).toEqual([]);
  });
});

describe("stripLayout", () => {
  it.each([2, 7, 30])(
    "spreads %i stops across one row with an alternating wobble",
    (count) => {
      // when
      const actual = stripLayout(count);

      // then
      expect(actual.stops).toHaveLength(count);
      expect(actual.stops[0]?.x).toBe(90);
      expect(actual.stops.every((stop) => stop.x >= 90 && stop.x <= 960)).toBe(
        true,
      );
      expect(actual.stops.map((stop) => stop.y)).toEqual(
        Array.from({ length: count }, (_, index) => (index % 2 ? 104 : 64)),
      );
      expect(actual.path.startsWith("M 20 96")).toBe(true);
      expect(actual.legs.map((leg) => leg.to)).toEqual(actual.stops);
      expect([actual.width, actual.height]).toEqual([1000, 130]);
    },
  );

  it("centres a lone stop", () => {
    // when
    const actual = stripLayout(1);

    // then
    expect(actual.stops).toEqual([{ x: 525, y: 64 }]);
    expect(actual.path.startsWith("M 20 96")).toBe(true);
  });

  it("reaches the far edge with the last of several stops", () => {
    // when
    const actual = stripLayout(7);

    // then
    expect(actual.stops.at(-1)?.x).toBe(960);
  });

  it("draws the road ahead from the trailhead when there is no stop", () => {
    // when
    const actual = stripLayout(0);

    // then
    expect(actual.start).toEqual({ x: 20, y: 96 });
    expect(actual.stops).toEqual([]);
    expect(actual.legs).toEqual([]);
    expect(actual.path.startsWith("M 20 96 C")).toBe(true);
    expect(actual.path.endsWith("980 70")).toBe(true);
  });

  it("never lays out more than thirty stops", () => {
    // when
    const actual = stripLayout(45);

    // then
    expect(actual.path).toBe(stripLayout(MAX_STOPS).path);
  });
});

describe("standingOfStops", () => {
  it("stands at the first In progress stop, else the last Done, else the start", () => {
    // when
    const actual = [
      standingOfStops([]),
      standingOfStops(["TODO", "TODO"]),
      standingOfStops(["DONE", "DONE", "TODO"]),
      standingOfStops(["DONE", "IN_PROGRESS", "IN_PROGRESS", "TODO"]),
    ];

    // then
    expect(actual).toEqual([0, 0, 1, 1]);
  });
});
