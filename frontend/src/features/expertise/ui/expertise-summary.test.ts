import { describe, expect, it } from "vitest";

import type { SelfArea, Tier } from "@/features/expertise/domain/expertise";
import { summarize } from "@/features/expertise/ui/expertise-summary";

let tagSeq = 0;

function area(
  tier: Tier,
  counts: Partial<{
    presented: number;
    contributed: number;
    offered: number;
  }> = {},
  hidden = false,
): SelfArea {
  tagSeq += 1;
  return {
    canonicalTag: `${tier}-${tagSeq}`,
    tier,
    hidden,
    communities: [],
    evidence: {
      presentedCount: counts.presented ?? 0,
      contributedCount: counts.contributed ?? 0,
      offeredCount: counts.offered ?? 0,
      recent: [],
    },
    communityTiers: [],
  };
}

describe("summarize", () => {
  it("returns the not-represented sentence for no areas", () => {
    const s = summarize([]);
    expect(s.standing).toMatch(/not yet represented/i);
    expect(s.totalSignals).toBe(0);
    expect(s.counts).toEqual({
      PRACTITIONER: 0,
      CONTRIBUTOR: 0,
      INTERESTED: 0,
      OPEN_TO_QUESTIONS: 0,
    });
  });

  it("leads with Practitioner standing and counts the rest as growing", () => {
    const s = summarize([
      area("PRACTITIONER"),
      area("PRACTITIONER"),
      area("CONTRIBUTOR"),
      area("INTERESTED"),
    ]);
    expect(s.standing).toBe("Practitioner in 2 areas and growing in 2 more.");
    expect(s.counts).toEqual({
      PRACTITIONER: 2,
      CONTRIBUTOR: 1,
      INTERESTED: 1,
      OPEN_TO_QUESTIONS: 0,
    });
  });

  it("uses singular wording and omits the growing clause at one area", () => {
    expect(summarize([area("PRACTITIONER")]).standing).toBe(
      "Practitioner in 1 area.",
    );
  });

  it("falls back to Contributor standing when there are no Practitioner areas", () => {
    expect(summarize([area("CONTRIBUTOR"), area("INTERESTED")]).standing).toBe(
      "Contributor in 1 area, exploring 1 more.",
    );
  });

  it("uses exploring wording when only Interested areas exist", () => {
    expect(summarize([area("INTERESTED"), area("INTERESTED")]).standing).toBe(
      "Exploring 2 areas.",
    );
  });

  it("sums all signal counts and hidden areas", () => {
    const s = summarize([
      area("PRACTITIONER", { presented: 3, contributed: 5, offered: 2 }),
      area("CONTRIBUTOR", { contributed: 3 }, true),
    ]);
    expect(s.totalSignals).toBe(13);
    expect(s.hiddenCount).toBe(1);
  });

  it("counts declared-only areas without counting them as signals", () => {
    // when
    const actual = summarize([area("CONTRIBUTOR", { contributed: 1 })], 2);

    // then
    expect(actual.counts.OPEN_TO_QUESTIONS).toBe(2);
    expect(actual.totalSignals).toBe(1);
    expect(actual.standing).toBe("Contributor in 1 area.");
  });

  it("states a declaration when there is no evidence yet", () => {
    expect(summarize([], 1).standing).toBe("Open to questions in 1 area.");
    expect(summarize([], 3).standing).toBe("Open to questions in 3 areas.");
  });
});
