import { describe, expect, it } from "vitest";

import {
  type ActivityTrace,
  activityTraceSchema,
  toActs,
  VISIBLE_CAP,
  visibleActs,
} from "@/features/health/domain/activity-trace";

function trace(overrides: Partial<ActivityTrace> = {}): ActivityTrace {
  const zeroes = Array.from({ length: 90 }, () => 0);
  return {
    days: 90,
    sessionsHeld: [...zeroes],
    libraryAdditions: [...zeroes],
    joins: [...zeroes],
    ...overrides,
  };
}

function withDay(day: number, count: number): number[] {
  const series = Array.from({ length: 90 }, () => 0);
  series[day] = count;
  return series;
}

describe("toActs", () => {
  it("turns daily counts into one act per count, freshest first", () => {
    // given - a session 89 days ago (index 0) and two joins today (index 89)
    const input = trace({ sessionsHeld: withDay(0, 1), joins: withDay(89, 2) });

    // when
    const actual = toActs(input);

    // then
    expect(actual).toEqual([
      { kind: "join", daysAgo: 0 },
      { kind: "join", daysAgo: 0 },
      { kind: "session", daysAgo: 89 },
    ]);
  });

  it("is empty for a trace of zeroes", () => {
    expect(toActs(trace())).toEqual([]);
  });
});

describe("visibleActs", () => {
  const acts = (count: number) =>
    Array.from({ length: count }, (_, index) => ({
      kind: "addition" as const,
      daysAgo: index,
    }));

  it("shows every act while there are only a few", () => {
    expect(visibleActs(acts(3))).toHaveLength(3);
  });

  it("saturates rather than growing with every act", () => {
    expect(visibleActs(acts(40))).toHaveLength(
      Math.round(44 * (1 - Math.exp(-40 / 22))),
    );
    expect(visibleActs(acts(1000)).length).toBeLessThanOrEqual(VISIBLE_CAP);
  });

  it("keeps the freshest", () => {
    const actual = visibleActs(acts(200));
    expect(actual[0]?.daysAgo).toBe(0);
    expect(Math.max(...actual.map((act) => act.daysAgo))).toBe(
      actual.length - 1,
    );
  });

  it("never shows more lights than there are acts", () => {
    expect(visibleActs(acts(1))).toHaveLength(1);
    expect(visibleActs([])).toEqual([]);
  });

  it("caps a burst day like any other", () => {
    const burst = toActs(trace({ joins: withDay(89, 300) }));
    expect(visibleActs(burst).length).toBeLessThanOrEqual(VISIBLE_CAP);
  });
});

describe("activityTraceSchema", () => {
  it("accepts the wire shape", () => {
    expect(activityTraceSchema.parse(trace())).toEqual(trace());
  });

  it("rejects a series of the wrong length", () => {
    expect(() =>
      activityTraceSchema.parse(trace({ joins: [1, 2, 3] })),
    ).toThrow();
  });

  it("rejects a negative count", () => {
    expect(() =>
      activityTraceSchema.parse(trace({ joins: withDay(0, -1) })),
    ).toThrow();
  });
});
