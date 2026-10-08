import { describe, expect, it } from "vitest";

import {
  ALL_COMMUNITIES,
  type AreaFilters,
  declarationsInCommunity,
  filterDeclarations,
} from "@/features/expertise/domain/area-filter";
import type { SelfDeclaration } from "@/features/expertise/domain/expertise";

const base = (over: Partial<AreaFilters> = {}): AreaFilters => ({
  query: "",
  communityId: ALL_COMMUNITIES,
  hasTalks: false,
  hasArtifacts: false,
  hiddenOnly: false,
  ...over,
});

function declaration(
  tag: string,
  over: Partial<SelfDeclaration> = {},
): SelfDeclaration {
  return {
    tag,
    communityId: 10,
    communityName: "Eng",
    communitySlug: "eng",
    hidden: false,
    declaredAt: "2026-09-12T12:00:00Z",
    ...over,
  };
}

describe("declarationsInCommunity", () => {
  it("keeps the declarations of the selected community, or all of them", () => {
    // given
    const declarations = [
      declaration("kafka"),
      declaration("spark", { communityId: 20 }),
    ];

    // when
    const actual = declarationsInCommunity(declarations, 20);

    // then
    expect(actual.map((d) => d.tag)).toEqual(["spark"]);
    expect(declarationsInCommunity(declarations, ALL_COMMUNITIES)).toEqual(
      declarations,
    );
  });
});

describe("filterDeclarations", () => {
  const declarations = [
    declaration("Kafka"),
    declaration("rust", { hidden: true }),
    declaration("spark", { communityId: 20 }),
  ];

  const tags = (filters: AreaFilters) =>
    filterDeclarations(declarations, filters).map((d) => d.tag);

  it("keeps every declaration with no filter set", () => {
    expect(tags(base())).toEqual(["Kafka", "rust", "spark"]);
  });

  it("matches the search text against the tag, case-insensitively", () => {
    expect(tags(base({ query: "kaf" }))).toEqual(["Kafka"]);
  });

  it("narrows to the selected community", () => {
    expect(tags(base({ communityId: 20 }))).toEqual(["spark"]);
  });

  it("drops every declaration once talks or artifacts are required", () => {
    expect(tags(base({ hasTalks: true }))).toEqual([]);
    expect(tags(base({ hasArtifacts: true }))).toEqual([]);
  });

  it("keeps only hidden declarations under Hidden only", () => {
    expect(tags(base({ hiddenOnly: true }))).toEqual(["rust"]);
  });
});
