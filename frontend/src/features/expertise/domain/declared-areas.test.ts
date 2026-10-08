import { describe, expect, it } from "vitest";

import {
  canDeclareFrom,
  declarationsIn,
  declaredOnly,
  hiddenTagCount,
  isDeclared,
} from "@/features/expertise/domain/declared-areas";
import type {
  SelfArea,
  SelfDeclaration,
  SelfView,
} from "@/features/expertise/domain/expertise";

function declaration(
  tag: string,
  communityId: number,
  communitySlug: string,
): SelfDeclaration {
  return {
    tag,
    communityId,
    communityName: communitySlug,
    communitySlug,
    hidden: false,
    declaredAt: "2026-09-12T12:00:00Z",
  };
}

function area(tag: string, communityId: number): SelfArea {
  return {
    canonicalTag: tag,
    tier: "CONTRIBUTOR",
    hidden: false,
    communities: [{ id: communityId, slug: "platform", name: "Platform" }],
    evidence: {
      presentedCount: 0,
      contributedCount: 1,
      offeredCount: 0,
      recent: [],
    },
    communityTiers: [],
  };
}

const VIEW: SelfView = {
  optedOut: false,
  areas: [area("kafka", 1)],
  declarations: [
    declaration("kafka", 1, "platform"),
    declaration("terraform", 1, "platform"),
    declaration("kafka", 2, "data"),
  ],
  declarableCommunities: [],
};

describe("declared-areas", () => {
  it("scopes declarations to a community when one is given", () => {
    expect(declarationsIn(VIEW, "data")).toEqual([
      declaration("kafka", 2, "data"),
    ]);
    expect(declarationsIn(VIEW)).toHaveLength(3);
  });

  it("treats an area as declared only where the declaration's community holds its evidence", () => {
    expect(isDeclared(area("kafka", 1), VIEW.declarations)).toBe(true);
    expect(isDeclared(area("kafka", 3), VIEW.declarations)).toBe(false);
  });

  it("keeps only the declarations no evidence in that community already shows", () => {
    expect(
      declaredOnly(VIEW).map((d) => `${d.communitySlug}:${d.tag}`),
    ).toEqual(["platform:terraform", "data:kafka"]);
    expect(declaredOnly(VIEW, "platform")).toEqual([
      declaration("terraform", 1, "platform"),
    ]);
  });

  it("counts each hidden tag once, whether evidence or a declaration carries it", () => {
    // given
    const view: SelfView = {
      optedOut: false,
      areas: [{ ...area("kafka", 1), hidden: true }, area("rust", 1)],
      declarations: [
        { ...declaration("kafka", 2, "data"), hidden: true },
        { ...declaration("terraform", 1, "platform"), hidden: true },
        declaration("go", 1, "platform"),
      ],
      declarableCommunities: [],
    };

    // when
    const actual = hiddenTagCount(view);

    // then
    expect(actual).toBe(2);
  });

  it("adds on a community's page only where the member can declare in that community", () => {
    // given
    const platform = { id: 1, slug: "platform", name: "Platform" };

    // then
    expect(canDeclareFrom([platform], "platform")).toBe(true);
    expect(canDeclareFrom([], "platform")).toBe(false);
    expect(canDeclareFrom([platform], "data")).toBe(false);
  });

  it("adds across communities wherever the member can declare", () => {
    expect(
      canDeclareFrom(
        [{ id: 1, slug: "platform", name: "Platform" }],
        undefined,
      ),
    ).toBe(true);
    expect(canDeclareFrom([], undefined)).toBe(false);
  });
});
