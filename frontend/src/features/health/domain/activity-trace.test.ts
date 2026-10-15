import { describe, expect, it } from "vitest";

import {
  type ActivityTrace,
  activityTraceSchema,
  toActs,
  VISIBLE_CAP,
  visibleActs,
  visibleActsOf,
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
  return Array.from({ length: 90 }, (_, index) => (index === day ? count : 0));
}

describe("toActs", () => {
  it("turns daily counts into one act per count, freshest first", () => {
    // given - a session 89 days ago (index 0) and two joins today (index 89)
    const input = trace({ sessionsHeld: withDay(0, 1), joins: withDay(89, 2) });

    // when
    const actual = toActs(input);

    // then
    expect(actual).toEqual([
      { kind: "join", daysAgo: 0, ordinal: 0 },
      { kind: "join", daysAgo: 0, ordinal: 1 },
      { kind: "session", daysAgo: 89, ordinal: 0 },
    ]);
  });

  it("keeps a day's sessions, then additions, then joins, and stops at the limit", () => {
    const input = trace({
      sessionsHeld: withDay(89, 1),
      libraryAdditions: withDay(89, 1),
      joins: withDay(89, 5),
    });
    expect(toActs(input, 4).map((act) => act.kind)).toEqual([
      "session",
      "addition",
      "join",
      "join",
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
      ordinal: 0,
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

describe("visibleActsOf", () => {
  const busy = trace({
    sessionsHeld: withDay(80, 2),
    libraryAdditions: withDay(88, 7),
    joins: withDay(89, 3),
  });

  it.each([
    ["a quiet trace", trace({ joins: withDay(10, 2) })],
    ["a busy trace", busy],
    ["a trace of zeroes", trace()],
  ])("is the visible acts of %s", (_, input) => {
    expect(visibleActsOf(input)).toEqual(visibleActs(toActs(input)));
  });

  it("caps a directory group's first-day joins at the freshest few", () => {
    const actual = visibleActsOf(trace({ joins: withDay(0, 20_000) }));
    expect(actual).toHaveLength(VISIBLE_CAP);
    expect(actual.at(-1)).toEqual({
      kind: "join",
      daysAgo: 89,
      ordinal: VISIBLE_CAP - 1,
    });
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
