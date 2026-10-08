import { describe, expect, it } from "vitest";

import type {
  MapContribution,
  MapPerson,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import {
  type MapContents,
  matchSummary,
  searchMap,
} from "@/features/expertise/ui/map-search";

function topic(canonicalTag: string): TopicNode {
  return {
    canonicalTag,
    practitionerCount: 1,
    signalCount: 1,
    topHolderShare: 1,
    topHolderName: "Ada",
    presentedCount: 0,
    contributedCount: 1,
    offeredCount: 0,
    lastActivityAt: null,
    openToQuestionsCount: 0,
  };
}

function contribution(
  signalId: number,
  canonicalTag: string,
  label: string | null,
  userId = "ada",
): MapContribution {
  return {
    signalId,
    canonicalTag,
    userId,
    reach: null,
    evidence: {
      signalType: "PRESENTED",
      sourceType: "SLOT",
      sourceId: signalId,
      label,
      communitySlug: "platform",
      occurredAt: "2026-09-01T10:00:00Z",
      sessionId: null,
      typeId: null,
      typeLabel: null,
      typeIcon: null,
      typeAccentColor: null,
    },
  };
}

const PEOPLE: ReadonlyMap<string, MapPerson> = new Map([
  ["ada", { userId: "ada", displayName: "Ada Lovelace", avatarUrl: null }],
  ["grace", { userId: "grace", displayName: "Grace Hopper", avatarUrl: null }],
]);

const CONTENTS: MapContents = {
  topics: [topic("kafka-streams"), topic("terraform")],
  items: [
    contribution(1, "kafka-streams", "Exactly once, at last"),
    contribution(2, "terraform", "Terraform at scale", "grace"),
    contribution(3, "terraform", null, "grace"),
  ],
  people: PEOPLE,
};

describe("searchMap", () => {
  it("answers nothing at all for a term that is only whitespace", () => {
    expect(searchMap("   ", CONTENTS)).toBeNull();
  });

  it("lights an area whose name matched, and every mark standing on it", () => {
    const found = searchMap("terraform", CONTENTS)!;

    expect([...found.tags]).toStrictEqual(["terraform"]);
    expect([...found.lit]).toStrictEqual([2, 3]);
  });

  it("lights a contribution its own title matched, leaving its area alone", () => {
    const found = searchMap("exactly once", CONTENTS)!;

    expect([...found.tags]).toStrictEqual([]);
    expect([...found.hits]).toStrictEqual([1]);
    expect([...found.lit]).toStrictEqual([1]);
  });

  it("lights a contribution by the name of the person who made it", () => {
    const found = searchMap("grace", CONTENTS)!;

    expect([...found.hits]).toStrictEqual([2, 3]);
  });

  /** A reader types what they read on the page, not the spelling the tags are stored in. */
  it("reads a term past its case, its accents and its punctuation", () => {
    const found = searchMap("  Kafka Streams ", CONTENTS)!;

    expect([...found.tags]).toStrictEqual(["kafka-streams"]);
  });

  /** Said in words beside the box instead: a map dimmed end to end reads as broken rather than as an answer. */
  it("answers with nothing lit when the term matched nothing", () => {
    const found = searchMap("prolog", CONTENTS)!;

    expect(found.tags.size).toBe(0);
    expect(found.hits.size).toBe(0);
    expect(found.lit.size).toBe(0);
  });

  /** An area matched by name and a contribution matched by its own title are one answer, not two. */
  it("counts a mark once when both it and its area matched", () => {
    const found = searchMap("terraform", CONTENTS)!;

    expect([...found.hits]).toStrictEqual([2]);
    expect([...found.lit]).toStrictEqual([2, 3]);
  });
});

describe("matchSummary", () => {
  const found = (tags: number, hits: number) => ({
    tags: new Set(Array.from({ length: tags }, (_, i) => `t${i}`)),
    hits: new Set(Array.from({ length: hits }, (_, i) => i)),
    lit: new Set<number>(),
  });

  it("counts the areas and the contributions apart", () => {
    expect(matchSummary(found(1, 1))).toBe("1 area, 1 contribution");
    expect(matchSummary(found(3, 2))).toBe("3 areas, 2 contributions");
  });

  /** "0 contributions" is a word about something a reader did not ask about; the half that answered is the answer. */
  it("says only the half that found anything", () => {
    expect(matchSummary(found(3, 0))).toBe("3 areas");
    expect(matchSummary(found(0, 2))).toBe("2 contributions");
  });

  it("says so in words when nothing matched", () => {
    expect(matchSummary(found(0, 0))).toBe("No matches");
  });
});
