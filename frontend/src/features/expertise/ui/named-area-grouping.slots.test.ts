import { expect, test } from "vitest";

import type {
  MapContribution,
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import type { NamedArea } from "@/features/expertise/domain/named-area";
import { placeContributions } from "@/features/expertise/ui/contribution-placement";
import { groupsFor } from "@/features/expertise/ui/named-area-grouping";
import { layoutTopics } from "@/features/expertise/ui/topic-graph-layout";

function nodes(tags: string[]): TopicNode[] {
  return tags.map((canonicalTag) => ({
    canonicalTag,
    practitionerCount: 1,
    signalCount: 1,
    topHolderShare: 1,
    topHolderName: "Someone",
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    lastActivityAt: "2026-08-01T00:00:00Z",
    openToQuestionsCount: 0,
  }));
}

function clique(tags: string[]): TopicLink[] {
  return tags.flatMap((a, i) =>
    tags.slice(i + 1).map((b) => ({ a, b, weight: 1 })),
  );
}

function cluster(prefix: string, size: number): string[] {
  return Array.from({ length: size }, (_, n) => `${prefix}${n + 1}`);
}

function contribution(signalId: number, tag: string): MapContribution {
  return {
    signalId,
    canonicalTag: tag,
    userId: "u1",
    reach: null,
    evidence: {
      signalType: "PRESENTED",
      sourceType: "SLOT",
      sourceId: signalId,
      label: null,
      communitySlug: "backend-guild",
      occurredAt: "2026-09-01T10:00:00Z",
      sessionId: null,
      typeId: null,
      typeLabel: null,
      typeIcon: null,
      typeAccentColor: null,
    },
  };
}

const LARGEST = cluster("a", 7);
const MIDDLE = cluster("b", 6);
const SMALLER = cluster("c", 5);
const SMALLEST = cluster("d", 4);
const TAGS = [...LARGEST, ...MIDDLE, ...SMALLER, ...SMALLEST];
const TOPICS = nodes(TAGS);
const LINKS = [LARGEST, MIDDLE, SMALLER, SMALLEST].flatMap(clique);
const SIZE = { width: 820, height: 610 };

/** The accent every area's marks are painted in, which is what a reader has learnt the map by. */
function accentsWith(namedAreas: NamedArea[]): Map<string, number> {
  const groups = groupsFor(TOPICS, LINKS, namedAreas);
  const placed = TAGS.map((tag) => ({
    tag,
    x: 0,
    y: 0,
    r: 10,
    declarations: 0,
    practised: true,
  }));
  const marks = placeContributions(
    TAGS.map((tag, i) => contribution(i + 1, tag)),
    placed,
    groups,
  );
  return new Map(marks.map((mark) => [mark.tag, mark.slot]));
}

function accentsOf(accents: Map<string, number>, tags: string[]): number[] {
  return tags.map((tag) => accents.get(tag)!);
}

test("naming the largest neighbourhood whole leaves every other neighbourhood its colour", () => {
  const before = accentsWith([]);
  const after = accentsWith([{ id: 1, name: "Platform", tags: LARGEST }]);

  const others = [...MIDDLE, ...SMALLER, ...SMALLEST];
  expect(accentsOf(after, others)).toStrictEqual(accentsOf(before, others));
});

test("a neighbourhood named whole keeps the colour it had as a neighbourhood", () => {
  const before = accentsWith([]);
  const after = accentsWith([{ id: 1, name: "Platform", tags: LARGEST }]);

  expect(accentsOf(after, LARGEST)).toStrictEqual(accentsOf(before, LARGEST));
});

test("shrinking a neighbourhood past a smaller one recolours neither", () => {
  const taken = MIDDLE.slice(0, 3);
  const before = accentsWith([]);
  const after = accentsWith([{ id: 1, name: "Part", tags: taken }]);

  const kept = TAGS.filter((tag) => !taken.includes(tag));
  expect(accentsOf(after, kept)).toStrictEqual(accentsOf(before, kept));
  expect(new Set(accentsOf(after, taken)).size).toBe(1);
  expect(accentsOf(before, kept)).not.toContain(accentsOf(after, taken)[0]);
});

test("naming the largest neighbourhood whole leaves the map's geography where it was", () => {
  const before = layoutTopics(
    TOPICS,
    LINKS,
    groupsFor(TOPICS, LINKS, []),
    SIZE,
  );
  const after = layoutTopics(
    TOPICS,
    LINKS,
    groupsFor(TOPICS, LINKS, [{ id: 1, name: "Platform", tags: LARGEST }]),
    SIZE,
  );

  expect(after).toStrictEqual(before);
});

/** The floor is the community's vocabulary, not what naming leaves of it: the real platform guild is 21 tags, 10 of them in its largest neighbourhood. */
test("naming the largest neighbourhood keeps every other one, even when what is left is under the vocabulary floor", () => {
  const big = cluster("p", 10);
  const middle = cluster("r", 6);
  const small = cluster("k", 5);
  const topics = nodes([...big, ...middle, ...small]);
  const links = [big, middle, small].flatMap(clique);
  const slotsOf = (namedAreas: NamedArea[]) =>
    groupsFor(topics, links, namedAreas).map((g) => [g.kind, g.anchor, g.slot]);

  expect(slotsOf([{ id: 1, name: "Platform", tags: big }])).toStrictEqual([
    ...slotsOf([]).slice(1),
    ["named", "p1", 0],
  ]);
});

/** The refine step after "Start from": a tag handed back must not take the neighbourhood's slot from the named area. */
test("a named area holding 9 of a neighbourhood's 10 tags keeps its slot from the tag left out", () => {
  const big = cluster("p", 10);
  const middle = cluster("r", 6);
  const small = cluster("k", 5);
  const topics = nodes([...big, ...middle, ...small]);
  const links = [big, middle, small].flatMap(clique);
  const slotOf = (namedAreas: NamedArea[]) =>
    new Map(
      groupsFor(topics, links, namedAreas).map((g) => [
        `${g.kind}:${g.anchor}`,
        g.slot,
      ]),
    );

  const before = slotOf([]);
  const after = slotOf([{ id: 1, name: "Platform", tags: big.slice(0, 9) }]);

  expect(after.get("named:p1")).toBe(before.get("neighbourhood:p1"));
  expect(after.get("neighbourhood:r1")).toBe(before.get("neighbourhood:r1"));
  expect(after.get("neighbourhood:k1")).toBe(before.get("neighbourhood:k1"));
});

test("a community under the vocabulary floor draws its named areas and no neighbourhood", () => {
  const small = cluster("s", 5);
  const tiny = cluster("t", 5);
  const topics = nodes([...small, ...tiny]);
  const links = [small, tiny].flatMap(clique);

  const groups = groupsFor(topics, links, [
    { id: 1, name: "Named", tags: small.slice(0, 2) },
  ]);

  expect(groups.map((g) => [g.kind, g.name, g.slot])).toStrictEqual([
    ["named", "Named", 0],
  ]);
});
