import { describe, expect, it } from "vitest";

import {
  planReplay,
  REPLAY,
  type ReplayCard,
  replaySummary,
} from "./replay-plan";

const card = (over: Partial<ReplayCard> = {}): ReplayCard => ({
  key: "a",
  newLoves: 2,
  finalCount: 7,
  inView: true,
  ...over,
});

describe("planReplay", () => {
  it("plans nothing when no card has new loves", () => {
    expect(
      planReplay([card({ newLoves: 0 }), card({ key: "b", newLoves: 0 })]),
    ).toEqual([]);
  });

  it("schedules one in-view card", () => {
    expect(planReplay([card()])).toEqual([
      { kind: "trail", key: "a", at: 250 },
      { kind: "tick", key: "a", count: 6, at: 250 },
      { kind: "trail", key: "a", at: 470 },
      { kind: "tick", key: "a", count: 7, at: 470 },
      { kind: "mark", key: "a", at: 370 },
      { kind: "summary", at: 470 + 720 + 200 },
    ]);
  });

  it("caps trails at three and lands on the final count", () => {
    const events = planReplay([card({ newLoves: 5 })]);
    expect(events.filter((e) => e.kind === "trail")).toHaveLength(3);
    const counts = events.flatMap((e) => (e.kind === "tick" ? [e.count] : []));
    expect(counts).toEqual([4, 5, 7]);
  });

  it("starts the second in-view card after the first finishes", () => {
    const events = planReplay([card(), card({ key: "b" })]);
    const trail = events.find((e) => e.kind === "trail" && e.key === "b");
    expect(trail?.at).toBe(250 + 2 * 220 + 260);
  });

  it("ticks and marks an off-view card at zero without advancing the clock", () => {
    const events = planReplay([card({ key: "off", inView: false }), card()]);
    expect(events.slice(0, 2)).toEqual([
      { kind: "tick", key: "off", count: 7, at: 0 },
      { kind: "mark", key: "off", at: 0 },
    ]);
    expect(events.find((e) => e.kind === "trail")?.at).toBe(250);
  });

  it("summarizes at zero when every card is off view", () => {
    expect(planReplay([card({ inView: false })]).at(-1)).toEqual({
      kind: "summary",
      at: 0,
    });
  });

  it("fits ten busy cards in the budget and keeps order", () => {
    const cards = Array.from({ length: 10 }, (_, i) =>
      card({ key: `k${i}`, newLoves: 3 }),
    );
    const events = planReplay(cards);
    const limit = REPLAY.BUDGET - REPLAY.FLIGHT - REPLAY.SETTLE;
    const summary = events.at(-1);
    expect(summary).toEqual({ kind: "summary", at: REPLAY.BUDGET });
    expect(events.slice(0, -1).every((e) => e.at <= limit)).toBe(true);
    expect(Math.max(...events.slice(0, -1).map((e) => e.at))).toBe(limit);
    expect(events.filter((e) => e.kind === "trail").map((e) => e.key)).toEqual(
      cards.flatMap((c) => [c.key, c.key, c.key]),
    );
  });

  it("collapses to zero under reduced motion", () => {
    const events = planReplay([card(), card({ key: "b", finalCount: 4 })], {
      reducedMotion: true,
    });
    expect(events.some((e) => e.kind === "trail")).toBe(false);
    expect(events.every((e) => e.at === 0)).toBe(true);
    expect(events).toContainEqual({ kind: "tick", key: "b", count: 4, at: 0 });
    expect(events.at(-1)).toEqual({ kind: "summary", at: 0 });
  });

  it("plans nothing under reduced motion without new loves", () => {
    expect(
      planReplay([card({ newLoves: 0 })], { reducedMotion: true }),
    ).toEqual([]);
  });
});

describe("replaySummary", () => {
  it.each([
    [{ total: 1, capped: false }, "1 new love since the last visit"],
    [{ total: 3, capped: false }, "3 new loves since the last visit"],
    [{ total: 12, capped: true }, "12 loves in the last 30 days"],
    [{ total: 1, capped: true }, "1 love in the last 30 days"],
  ])("%j", (input, expected) => {
    expect(replaySummary(input)).toBe(expected);
  });
});
