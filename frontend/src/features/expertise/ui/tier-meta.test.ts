import { describe, expect, it } from "vitest";

import { TIER_META, TIER_ORDER } from "@/features/expertise/ui/tier-meta";

describe("tier-meta", () => {
  it("orders tiers strongest-first, a declaration last", () => {
    expect(TIER_ORDER).toEqual([
      "PRACTITIONER",
      "CONTRIBUTOR",
      "INTERESTED",
      "OPEN_TO_QUESTIONS",
    ]);
  });

  it("labels each tier", () => {
    expect(TIER_META.PRACTITIONER.label).toBe("Practitioner");
    expect(TIER_META.CONTRIBUTOR.label).toBe("Contributor");
    expect(TIER_META.INTERESTED.label).toBe("Interested");
    expect(TIER_META.OPEN_TO_QUESTIONS.label).toBe("Open to questions");
  });

  it("gives each tier a distinct dot class", () => {
    const classes = TIER_ORDER.map((tier) => TIER_META[tier].dotClassName);
    expect(new Set(classes).size).toBe(TIER_ORDER.length);
  });
});
