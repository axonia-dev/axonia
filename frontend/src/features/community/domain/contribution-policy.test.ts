import { describe, expect, it } from "vitest";

import {
  contributionPolicySchema,
  curated,
} from "@/features/community/domain/contribution-policy";

describe("contributionPolicySchema", () => {
  it("parses a valid policy", () => {
    // given
    const raw = {
      canStartSeries: true,
      canEditStructure: false,
      canQueueTopics: true,
      canManageSchedule: true,
    };

    // when
    const actual = contributionPolicySchema.parse(raw);

    // then
    expect(actual).toEqual(raw);
  });

  it("rejects a payload with a missing axis", () => {
    // given
    const raw = { canStartSeries: true, canEditStructure: false };

    // when
    const actual = contributionPolicySchema.safeParse(raw);

    // then
    expect(actual.success).toBe(false);
  });
});

describe("curated", () => {
  it("locks every axis", () => {
    // when
    const actual = curated();

    // then
    expect(actual).toEqual({
      canStartSeries: false,
      canEditStructure: false,
      canQueueTopics: false,
      canManageSchedule: false,
    });
  });
});
