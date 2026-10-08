import { describe, expect, it } from "vitest";

import {
  loveSummarySchema,
  recognizedContributionSchema,
} from "@/features/recognition/domain/recognition";

describe("recognition schemas", () => {
  it("parses a love summary", () => {
    // when
    const actual = loveSummarySchema.parse({ count: 2, viewerHasLoved: true });

    // then
    expect(actual.count).toBe(2);
    expect(actual.viewerHasLoved).toBe(true);
  });

  it("parses a recognized contribution with its love count", () => {
    // when
    const actual = recognizedContributionSchema.parse({
      contributionType: "ARTIFACT_CAPTURED",
      contributionId: 7,
      communityId: 1,
      communitySlug: null,
      title: "Retry guide",
      occurredAt: "2026-06-01T00:00:00Z",
      loveCount: 3,
    });

    // then
    expect(actual.loveCount).toBe(3);
  });

  it("rejects the retired topic contribution type", () => {
    // when
    const actual = recognizedContributionSchema.safeParse({
      contributionType: "TOPIC_PROPOSED",
      contributionId: 7,
      communityId: 1,
      title: "T",
      occurredAt: "2026-06-01T00:00:00Z",
      loveCount: 0,
    });

    // then
    expect(actual.success).toBe(false);
  });
});

describe("recognizedContributionSchema", () => {
  it("parses a contribution carrying a community slug", () => {
    const parsed = recognizedContributionSchema.parse({
      contributionType: "SLOT_PRESENTED",
      contributionId: 9,
      communityId: 1,
      communitySlug: "eng",
      title: "Resilience talk",
      occurredAt: "2026-06-01T00:00:00Z",
      loveCount: 2,
    });
    expect(parsed.communitySlug).toBe("eng");
  });

  it("accepts a null community slug (unlinkable contribution)", () => {
    const parsed = recognizedContributionSchema.parse({
      contributionType: "ARTIFACT_CAPTURED",
      contributionId: 3,
      communityId: 1,
      communitySlug: null,
      title: "Runbook",
      occurredAt: "2026-06-01T00:00:00Z",
      loveCount: 0,
    });
    expect(parsed.communitySlug).toBeNull();
  });

  it("parses communityName and defaults it to null when absent", () => {
    const parsed = recognizedContributionSchema.parse({
      contributionType: "ARTIFACT_CAPTURED",
      contributionId: 1,
      communityId: 2,
      communitySlug: "eng",
      communityName: "Engineering",
      title: "RFC",
      occurredAt: "2026-01-01T00:00:00Z",
      loveCount: 0,
    });
    expect(parsed.communityName).toBe("Engineering");

    const without = recognizedContributionSchema.parse({
      contributionType: "ARTIFACT_CAPTURED",
      contributionId: 1,
      communityId: 2,
      communitySlug: null,
      title: "RFC",
      occurredAt: "2026-01-01T00:00:00Z",
      loveCount: 0,
    });
    expect(without.communityName).toBeNull();
  });
});
