import { describe, expect, it } from "vitest";

import { pairingOf } from "@/features/mentoring/test-data";
import {
  byNextMeeting,
  ordinalWord,
  summitsOf,
  togetherOrdinal,
} from "@/features/mentoring/ui/my-mentoring/mentoring-groups";

const ids = (pairings: { id: number }[]) => pairings.map(({ id }) => id);

describe("byNextMeeting", () => {
  it("puts dated pairings first, earliest day first, then the rest newest first", () => {
    // given
    const pairings = [
      pairingOf({ id: 1, startedAt: "2026-01-01T10:00:00Z" }),
      pairingOf({ id: 2, nextMeeting: { on: "2026-11-20", passed: false } }),
      pairingOf({ id: 3, startedAt: "2026-06-01T10:00:00Z" }),
      pairingOf({ id: 4, nextMeeting: { on: "2026-11-12", passed: true } }),
      pairingOf({ id: 5, nextMeeting: { on: "2026-11-18", passed: false } }),
    ];

    // when
    const actual = byNextMeeting(pairings);

    // then
    expect(ids(actual)).toEqual([4, 5, 2, 3, 1]);
    expect(ids(pairings)).toEqual([1, 2, 3, 4, 5]);
  });
});

describe("togetherOrdinal", () => {
  const alice = (id: number, startedAt: string) =>
    pairingOf({ id, partnerId: "alice", startedAt });
  const all = [
    alice(3, "2026-09-01T10:00:00Z"),
    alice(1, "2025-01-01T10:00:00Z"),
    alice(2, "2025-06-01T10:00:00Z"),
    pairingOf({ id: 4, partnerId: "alice", myRole: "MENTOR" }),
    pairingOf({ id: 5, partnerId: "bob" }),
  ];

  it("counts a pairing among the earlier ones with the same partner in the same roles", () => {
    // when
    const actual = all.map((pairing) => togetherOrdinal(pairing, all));

    // then
    expect(actual).toEqual([3, 1, 2, null, null]);
  });

  it("never counts pairings with an erased partner together", () => {
    // given
    const erased = [
      pairingOf({ id: 6, partnerId: null }),
      pairingOf({ id: 7, partnerId: null }),
    ];

    // when
    const actual = erased.map((pairing) => togetherOrdinal(pairing, erased));

    // then
    expect(actual).toEqual([null, null]);
  });
});

describe("summitsOf", () => {
  it("groups by partner and role, newest summit and newest pairing first", () => {
    // given
    const ended = [
      pairingOf({ id: 1, partnerId: "alice", endedAt: "2025-03-01T10:00:00Z" }),
      pairingOf({ id: 2, partnerId: "bob", endedAt: "2026-02-01T10:00:00Z" }),
      pairingOf({
        id: 3,
        partnerId: "alice",
        partnerName: "Alice B.",
        endedAt: "2026-05-01T10:00:00Z",
      }),
      pairingOf({
        id: 4,
        partnerId: "alice",
        myRole: "MENTOR",
        endedAt: "2024-01-01T10:00:00Z",
      }),
    ];

    // when
    const actual = summitsOf(ended);

    // then
    expect(actual.map((summit) => ids(summit.pairings))).toEqual([
      [3, 1],
      [2],
      [4],
    ]);
    expect(actual[0]).toMatchObject({
      partnerId: "alice",
      partnerName: "Alice B.",
      myRole: "MENTEE",
    });
    expect(new Set(actual.map((summit) => summit.key)).size).toBe(3);
  });

  it("keeps each pairing with an erased partner as its own summit", () => {
    // given
    const ended = [
      pairingOf({ id: 1, partnerId: null, endedAt: "2026-01-01T10:00:00Z" }),
      pairingOf({ id: 2, partnerId: null, endedAt: "2026-02-01T10:00:00Z" }),
    ];

    // when
    const actual = summitsOf(ended);

    // then
    expect(actual.map((summit) => ids(summit.pairings))).toEqual([[2], [1]]);
    expect(new Set(actual.map((summit) => summit.key)).size).toBe(2);
  });

  it("has no summit without an ended pairing", () => {
    // when
    const actual = summitsOf([]);

    // then
    expect(actual).toEqual([]);
  });
});

describe("ordinalWord", () => {
  it("spells the first ten and numbers the rest", () => {
    // when
    const actual = [2, 3, 10, 11, 12, 13, 21, 22, 23, 24, 101, 111].map(
      ordinalWord,
    );

    // then
    expect(actual).toEqual([
      "second",
      "third",
      "tenth",
      "11th",
      "12th",
      "13th",
      "21st",
      "22nd",
      "23rd",
      "24th",
      "101st",
      "111th",
    ]);
  });
});
