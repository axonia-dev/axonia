import { describe, expect, it } from "vitest";

import {
  ALL_COMMUNITIES,
  filterContributions,
  isLoved,
  scopeToCommunity,
  sortContributions,
} from "@/features/recognition/domain/contribution-filter";
import type { RecognizedContribution } from "@/features/recognition/domain/recognition";

function contribution(
  over: Partial<RecognizedContribution> = {},
): RecognizedContribution {
  return {
    contributionType: "ARTIFACT_CAPTURED",
    contributionId: 1,
    communityId: 10,
    communitySlug: "eng",
    communityName: "Engineering",
    title: "A talk",
    occurredAt: "2026-01-01T00:00:00Z",
    loveCount: 0,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
    ...over,
  };
}

const base = {
  query: "",
  communityId: ALL_COMMUNITIES,
  kind: "ALL" as const,
  lovedOnly: false,
};

describe("filterContributions", () => {
  it("matches query against title and typeLabel, case-insensitively", () => {
    const items = [
      contribution({ contributionId: 1, title: "Kafka deep dive" }),
      contribution({
        contributionId: 2,
        title: "Other",
        typeLabel: "Workshop",
      }),
      contribution({ contributionId: 3, title: "Nope", typeLabel: "RFC" }),
    ];
    expect(
      filterContributions(items, { ...base, query: "kafka" }).map(
        (c) => c.contributionId,
      ),
    ).toEqual([1]);
    expect(
      filterContributions(items, { ...base, query: "workshop" }).map(
        (c) => c.contributionId,
      ),
    ).toEqual([2]);
  });

  it("filters by community id", () => {
    const items = [
      contribution({ contributionId: 1, communityId: 10 }),
      contribution({ contributionId: 2, communityId: 20 }),
    ];
    expect(
      filterContributions(items, { ...base, communityId: 20 }).map(
        (c) => c.contributionId,
      ),
    ).toEqual([2]);
    expect(
      filterContributions(items, { ...base, communityId: ALL_COMMUNITIES }),
    ).toHaveLength(2);
  });

  it("filters by kind", () => {
    const items = [
      contribution({ contributionId: 1, contributionType: "SLOT_PRESENTED" }),
      contribution({
        contributionId: 2,
        contributionType: "ARTIFACT_CAPTURED",
      }),
    ];
    expect(
      filterContributions(items, { ...base, kind: "SLOT_PRESENTED" }).map(
        (c) => c.contributionId,
      ),
    ).toEqual([1]);
  });

  it("keeps only loved when lovedOnly is set", () => {
    const items = [
      contribution({ contributionId: 1, loveCount: 0 }),
      contribution({ contributionId: 2, loveCount: 3 }),
    ];
    expect(
      filterContributions(items, { ...base, lovedOnly: true }).map(
        (c) => c.contributionId,
      ),
    ).toEqual([2]);
  });
});

describe("scopeToCommunity", () => {
  it("keeps only items belonging to the given community", () => {
    const items = [
      contribution({ contributionId: 1, communityId: 10 }),
      contribution({ contributionId: 2, communityId: 20 }),
    ];
    expect(scopeToCommunity(items, 20).map((c) => c.contributionId)).toEqual([
      2,
    ]);
  });

  it("keeps nothing while the community is not yet resolved", () => {
    const items = [contribution({ contributionId: 1 })];
    expect(scopeToCommunity(items, undefined)).toEqual([]);
  });
});

describe("isLoved", () => {
  it("is true only when loveCount > 0", () => {
    expect(isLoved(contribution({ loveCount: 0 }))).toBe(false);
    expect(isLoved(contribution({ loveCount: 1 }))).toBe(true);
  });
});

describe("sortContributions", () => {
  const a = contribution({
    contributionId: 1,
    title: "Beta",
    occurredAt: "2026-01-01T00:00:00Z",
    loveCount: 1,
  });
  const b = contribution({
    contributionId: 2,
    title: "Alpha",
    occurredAt: "2026-03-01T00:00:00Z",
    loveCount: 9,
  });

  it("recent: newest occurredAt first", () => {
    expect(
      sortContributions([a, b], "recent").map((c) => c.contributionId),
    ).toEqual([2, 1]);
  });
  it("oldest: oldest occurredAt first", () => {
    expect(
      sortContributions([a, b], "oldest").map((c) => c.contributionId),
    ).toEqual([1, 2]);
  });
  it("loved: highest loveCount first", () => {
    expect(
      sortContributions([a, b], "loved").map((c) => c.contributionId),
    ).toEqual([2, 1]);
  });
  it("az: alphabetical by title", () => {
    expect(
      sortContributions([a, b], "az").map((c) => c.contributionId),
    ).toEqual([2, 1]);
  });
  it("does not mutate the input", () => {
    const input = [a, b];
    sortContributions(input, "az");
    expect(input.map((c) => c.contributionId)).toEqual([1, 2]);
  });
});
