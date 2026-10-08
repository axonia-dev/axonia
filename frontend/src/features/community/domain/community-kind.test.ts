import { describe, expect, it } from "vitest";

import {
  type CommunityKind,
  communityKindSchema,
} from "@/features/community/domain/community-kind";

const guild: CommunityKind = { id: 1, label: "Guild", communityCount: 3 };

describe("communityKindSchema", () => {
  it("parses a valid kind", () => {
    // given the `guild` fixture above

    // when
    const actual = communityKindSchema.parse(guild);

    // then
    expect(actual).toEqual(guild);
  });

  it("rejects a missing label", () => {
    // given
    const invalid = { id: 1 };

    // when / then
    expect(() => communityKindSchema.parse(invalid)).toThrow();
  });

  it("rejects a missing communityCount", () => {
    // given
    const invalid = { id: 1, label: "Guild" };

    // when / then
    expect(() => communityKindSchema.parse(invalid)).toThrow();
  });
});
