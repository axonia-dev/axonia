import { describe, expect, it } from "vitest";

import type { MapContribution } from "@/features/expertise/domain/expertise";
import { placeContributions } from "@/features/expertise/ui/contribution-placement";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

const kafka: PlacedTopic = {
  tag: "kafka",
  x: 100,
  y: 200,
  r: 20,
  declarations: 0,
  practised: true,
};

function contribution(
  signalId: number,
  tag = "kafka",
  sourceType: MapContribution["evidence"]["sourceType"] = "SLOT",
): MapContribution {
  return {
    signalId,
    canonicalTag: tag,
    userId: "u1",
    reach: null,
    evidence: {
      signalType: "PRESENTED",
      sourceType,
      sourceId: signalId,
      label: null,
      communitySlug: "backend-guild",
      occurredAt: "2026-09-01T10:00:00Z",
      sessionId: null,
      typeId: null,
      typeLabel: null,
      typeIcon: null,
      typeAccentColor: null,
    },
  };
}

describe("placeContributions", () => {
  it("is deterministic: the same input places every mark identically", () => {
    const input = [contribution(1), contribution(2), contribution(3)];

    expect(placeContributions(input, [kafka], [])).toEqual(
      placeContributions(input, [kafka], []),
    );
  });

  // A small radius on purpose: spread is max(topic radius, sqrt(count) * k), so at kafka's r of 20
  // the floor dominates and any spread rule at all, global included, would place these identically.
  const narrow: PlacedTopic = { ...kafka, tag: "redis", r: 6 };
  const postgres: PlacedTopic = { ...kafka, tag: "postgres", x: 400, r: 6 };

  it("does not move a mark when an unrelated contribution arrives", () => {
    const one = contribution(1, "redis");
    const elsewhere = Array.from({ length: 30 }, (_, i) =>
      contribution(100 + i, "postgres"),
    );

    const before = placeContributions([one], [narrow, postgres], []);
    const after = placeContributions(
      [one, ...elsewhere],
      [narrow, postgres],
      [],
    );

    expect(after[0]).toEqual(before[0]);
  });

  it("widens an area's own disc as that area fills up", () => {
    const one = contribution(1, "redis");
    const crowd = Array.from({ length: 30 }, (_, i) =>
      contribution(200 + i, "redis"),
    );

    const alone = placeContributions([one], [narrow], [])[0]!;
    const crowded = placeContributions([one, ...crowd], [narrow], [])[0]!;

    expect(
      Math.hypot(crowded.x - narrow.x, crowded.y - narrow.y),
    ).toBeGreaterThan(Math.hypot(alone.x - narrow.x, alone.y - narrow.y));
  });

  it("does not move a mark when the input order changes", () => {
    const a = placeContributions(
      [contribution(1), contribution(2)],
      [kafka],
      [],
    );
    const b = placeContributions(
      [contribution(2), contribution(1)],
      [kafka],
      [],
    );

    expect(b.find((m) => m.signalId === 1)).toEqual(
      a.find((m) => m.signalId === 1),
    );
  });

  it("scatters rather than stacking", () => {
    const marks = placeContributions(
      Array.from({ length: 20 }, (_, i) => contribution(i + 1)),
      [kafka],
      [],
    );
    const distinct = new Set(marks.map((m) => `${m.x},${m.y}`));

    expect(distinct.size).toBe(20);
  });

  it("keeps a mark near its own area", () => {
    const marks = placeContributions(
      Array.from({ length: 50 }, (_, i) => contribution(i + 1)),
      [kafka],
      [],
    );

    for (const mark of marks) {
      const distance = Math.hypot(mark.x - kafka.x, mark.y - kafka.y);
      expect(distance).toBeLessThan(80);
    }
  });

  it("keeps two crowded neighbours' discs apart", () => {
    const left: PlacedTopic = { ...kafka, tag: "left", x: 0, y: 0, r: 6 };
    const right: PlacedTopic = { ...kafka, tag: "right", x: 60, y: 0, r: 6 };
    const crowd = (tag: string, from: number) =>
      Array.from({ length: 300 }, (_, i) => contribution(from + i, tag));

    const marks = placeContributions(
      [...crowd("left", 1), ...crowd("right", 1000)],
      [left, right],
      [],
    );

    const gap = Math.hypot(right.x - left.x, right.y - left.y);
    for (const mark of marks) {
      const own = mark.tag === "left" ? left : right;
      expect(Math.hypot(mark.x - own.x, mark.y - own.y)).toBeLessThan(gap / 2);
    }
  });

  // A deep area keeps growing with the square root of its crowd: sqrt(800/300) is about 1.63,
  // so 1.4 is clear of both the true ratio and any ceiling that would flatten it.
  it("spreads 800 contributions wider than 300, with room to spare", () => {
    const home: PlacedTopic = { ...kafka, tag: "home", x: 0, y: 0, r: 6 };
    const far: PlacedTopic = { ...kafka, tag: "far", x: 1000, y: 0, r: 6 };
    const reach = (count: number) => {
      const marks = placeContributions(
        Array.from({ length: count }, (_, i) => contribution(i + 1, "home")),
        [home, far],
        [],
      );
      return Math.max(...marks.map((m) => Math.hypot(m.x, m.y)));
    };

    expect(reach(800)).toBeGreaterThan(reach(300) * 1.4);
  });

  it("drops a contribution whose area is not on the map", () => {
    expect(placeContributions([contribution(1, "rust")], [kafka], [])).toEqual(
      [],
    );
  });

  it("carries the slot of the group its area belongs to, not the group's place in the list", () => {
    const groups = [
      { tags: ["redis"], slot: 0 },
      { tags: ["kafka"], slot: 3 },
    ];

    expect(
      placeContributions([contribution(1)], [kafka], groups)[0]?.slot,
    ).toBe(3);
  });

  it("gives an area on a map with no groups slot zero", () => {
    expect(placeContributions([contribution(1)], [kafka], [])[0]?.slot).toBe(0);
  });

  // Beside a named area the rest of a small map is detected into nothing, and in the first group's colour it would
  // read as part of the area the leader named.
  it("gives an area in no group a colour of its own, apart from every group", () => {
    const groups = [
      { tags: ["redis"], slot: 2 },
      { tags: ["postgres"], slot: 0 },
    ];

    expect(
      placeContributions([contribution(1)], [kafka], groups)[0]?.slot,
    ).toBe(3);
  });

  it("carries the occurrence as epoch milliseconds", () => {
    expect(
      placeContributions([contribution(1)], [kafka], [])[0]?.occurredAt,
    ).toBe(Date.parse("2026-09-01T10:00:00Z"));
  });
});
