import { describe, expect, it } from "vitest";

import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { summarizeImpact } from "@/features/recognition/ui/impact-summary";

function contribution(
  over: Partial<RecognizedContribution>,
): RecognizedContribution {
  return {
    contributionType: "SLOT_PRESENTED",
    contributionId: 1,
    communityId: 1,
    communitySlug: "eng",
    communityName: "Engineering",
    title: "A talk",
    occurredAt: "2026-06-01T00:00:00Z",
    loveCount: 1,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
    ...over,
  };
}

describe("summarizeImpact", () => {
  it("invites the first contribution when there are none", () => {
    expect(summarizeImpact([]).standing).toBe(
      "Contributions appear here as people appreciate them.",
    );
  });

  it("counts talks and artifacts across distinct communities", () => {
    const summary = summarizeImpact([
      contribution({ contributionType: "SLOT_PRESENTED", communityId: 1 }),
      contribution({ contributionType: "SLOT_PRESENTED", communityId: 2 }),
      contribution({ contributionType: "ARTIFACT_CAPTURED", communityId: 2 }),
    ]);
    expect(summary.counts.SLOT_PRESENTED).toBe(2);
    expect(summary.counts.ARTIFACT_CAPTURED).toBe(1);
    expect(summary.communityCount).toBe(2);
    expect(summary.total).toBe(3);
    expect(summary.standing).toBe(
      "Contributed 2 talks and 1 artifact, appreciated by peers across 2 communities.",
    );
  });

  it("reads naturally with only one type and one community", () => {
    expect(
      summarizeImpact([contribution({ contributionType: "ARTIFACT_CAPTURED" })])
        .standing,
    ).toBe("Contributed 1 artifact, appreciated by peers in one community.");
  });

  it("names no community inside a community's own page", () => {
    expect(
      summarizeImpact([contribution({ contributionType: "SLOT_PRESENTED" })], {
        scoped: true,
      }).standing,
    ).toBe("Contributed 1 talk, appreciated by peers.");
  });
});
