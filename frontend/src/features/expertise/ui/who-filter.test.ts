import { describe, expect, it } from "vitest";

import type { Practitioner, Tier } from "@/features/expertise/domain/expertise";

import { countsByWho, filterByWho, parseWho } from "./who-filter";

function person(userId: string, tier: Tier, open = false): Practitioner {
  return {
    userId,
    displayName: userId,
    avatarUrl: null,
    orgUnit: null,
    tier,
    evidence: {
      presentedCount: 0,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    openToQuestions: open,
    declaredAt: open ? "2026-11-01T00:00:00Z" : null,
  };
}

const people = [
  person("ana", "PRACTITIONER", true),
  person("bo", "CONTRIBUTOR"),
  person("cy", "INTERESTED"),
  person("di", "OPEN_TO_QUESTIONS", true),
];

describe("filterByWho", () => {
  it("keepsEveryoneForAnyone", () => {
    // when
    const actual = filterByWho(people, "anyone");

    // then
    expect(actual.map((p) => p.userId)).toEqual(["ana", "bo", "cy", "di"]);
  });

  it("keepsPractitionersAndContributorsForDemonstrated", () => {
    // when
    const actual = filterByWho(people, "demonstrated");

    // then
    expect(actual.map((p) => p.userId)).toEqual(["ana", "bo"]);
  });

  it("keepsOpenPeopleInOrderForOpen", () => {
    // when
    const actual = filterByWho(people, "open");

    // then
    expect(actual.map((p) => p.userId)).toEqual(["ana", "di"]);
  });
});

describe("countsByWho", () => {
  it("countsEachNarrowing", () => {
    // when
    const actual = countsByWho(people);

    // then
    expect(actual).toEqual({ anyone: 4, demonstrated: 2, open: 2 });
  });
});

describe("parseWho", () => {
  it("readsAKnownNarrowingAndFallsBackToAnyone", () => {
    // when
    const actual = [
      parseWho("open"),
      parseWho("demonstrated"),
      parseWho("x"),
      parseWho(null),
    ];

    // then
    expect(actual).toEqual(["open", "demonstrated", "anyone", "anyone"]);
  });
});
