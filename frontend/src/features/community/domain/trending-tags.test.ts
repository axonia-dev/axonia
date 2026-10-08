import { describe, expect, it } from "vitest";

import { trendingTags } from "@/features/community/domain/trending-tags";
import type { Artifact } from "@/features/knowledge/domain/artifact";

const withTags = (tags: string[]) => ({ tags }) as unknown as Artifact;

describe("trendingTags", () => {
  it("counts tags and orders by frequency then alphabetically", () => {
    const result = trendingTags([
      withTags(["rfc", "incident"]),
      withTags(["incident"]),
      withTags(["onboarding"]),
    ]);
    expect(result).toEqual([
      { tag: "incident", count: 2 },
      { tag: "onboarding", count: 1 },
      { tag: "rfc", count: 1 },
    ]);
  });

  it("honours the limit", () => {
    const result = trendingTags([withTags(["a", "b", "c", "d"])], 2);
    expect(result).toHaveLength(2);
  });

  it("returns an empty array when there are no tags", () => {
    expect(trendingTags([withTags([])])).toEqual([]);
  });
});
