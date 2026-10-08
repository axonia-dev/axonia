import { describe, expect, it } from "vitest";

import { selfViewSchema } from "@/features/expertise/domain/expertise";

describe("selfViewSchema", () => {
  it("reads each community's tier and slug, and leaves them unknown in an older payload without them", () => {
    const withTiers = {
      optedOut: false,
      areas: [
        {
          canonicalTag: "kafka",
          tier: "PRACTITIONER",
          hidden: false,
          communities: [{ id: 1, slug: "platform", name: "Platform Guild" }],
          communityTiers: [{ communityId: 1, tier: "CONTRIBUTOR" }],
          evidence: {
            presentedCount: 0,
            contributedCount: 1,
            offeredCount: 0,
            recent: [],
          },
        },
      ],
    };
    const parsed = selfViewSchema.parse(withTiers);
    expect(parsed.areas[0]!.communities[0]!.slug).toBe("platform");
    expect(parsed.areas[0]!.communityTiers).toEqual([
      { communityId: 1, tier: "CONTRIBUTOR" },
    ]);

    const older = {
      optedOut: false,
      areas: [
        {
          canonicalTag: "kafka",
          tier: "PRACTITIONER",
          hidden: false,
          communities: [{ id: 1, name: "Platform Guild" }],
          evidence: {
            presentedCount: 0,
            contributedCount: 1,
            offeredCount: 0,
            recent: [],
          },
        },
      ],
    };
    const parsedOlder = selfViewSchema.parse(older);
    expect(parsedOlder.areas[0]!.communityTiers).toBeUndefined();
    expect(parsedOlder.areas[0]!.communities[0]!.slug).toBeUndefined();
  });
});
