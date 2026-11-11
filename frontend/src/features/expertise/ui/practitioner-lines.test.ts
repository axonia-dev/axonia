import { describe, expect, it } from "vitest";

import type { Practitioner } from "@/features/expertise/domain/expertise";

import { declarationLine } from "./practitioner-lines";

const NOW = new Date("2026-11-11T10:00:00Z");

const declaredOnly: Practitioner = {
  userId: "marc",
  displayName: "Marc Brunner",
  avatarUrl: null,
  orgUnit: null,
  tier: "OPEN_TO_QUESTIONS",
  evidence: {
    presentedCount: 0,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
  },
  openToQuestions: true,
  declaredAt: "2026-09-30T23:30:00Z",
};

describe("declarationLine", () => {
  it("names the month of the declaration in UTC for a declaration-only person", () => {
    // when
    const actual = declarationLine(declaredOnly, NOW);

    // then
    expect(actual).toBe("Declared in Sep, no activity here yet");
  });

  it("names the year of a declaration from an earlier year", () => {
    // when
    const actual = declarationLine(
      { ...declaredOnly, declaredAt: "2025-11-02T09:00:00Z" },
      NOW,
    );

    // then
    expect(actual).toBe("Declared in Nov 2025, no activity here yet");
  });

  it("leaves a person with evidence to the Ask about pill", () => {
    // when
    const actual = declarationLine(
      { ...declaredOnly, tier: "PRACTITIONER" },
      NOW,
    );

    // then
    expect(actual).toBeNull();
  });

  it("prints nothing for a person not open to questions", () => {
    // when
    const actual = declarationLine(
      { ...declaredOnly, openToQuestions: false },
      NOW,
    );

    // then
    expect(actual).toBeNull();
  });
});
