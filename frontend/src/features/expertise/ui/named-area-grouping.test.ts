import { expect, test } from "vitest";

import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import type { NamedArea } from "@/features/expertise/domain/named-area";
import {
  type DrawnGroup,
  groupNames,
  groupsFor,
} from "@/features/expertise/ui/named-area-grouping";
import {
  detectNeighbourhoods,
  neighbourhoodNames,
} from "@/features/expertise/ui/topic-neighbourhoods";

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

function cluster(prefix: string): string[] {
  return [1, 2, 3, 4, 5, 6].map((n) => `${prefix}${n}`);
}

const A = cluster("a");
const B = cluster("b");
const C = cluster("c");
const TOPICS = nodes([...A, ...B, ...C]);
const LINKS = [...clique(A), ...clique(B), ...clique(C)];

function named(name: string, tags: string[], id = 1): NamedArea {
  return { id, name, tags };
}

test("with no named areas the map groups exactly as detection always has", () => {
  const detected = detectNeighbourhoods(TOPICS, LINKS);

  expect(groupsFor(TOPICS, LINKS, [])).toStrictEqual(
    detected.map((n, slot) => ({
      ...n,
      name: neighbourhoodNames(n).list,
      kind: "neighbourhood",
      slot,
    })),
  );
  expect(detected).toHaveLength(3);
});

test("a named area follows the detected neighbourhoods and says it was named", () => {
  const groups = groupsFor(TOPICS, LINKS, [named("Streaming", A)]);

  expect(groups.map((g) => g.kind)).toStrictEqual([
    "neighbourhood",
    "neighbourhood",
    "named",
  ]);
  expect(groups[2]).toMatchObject({ name: "Streaming", tags: A });
});

/** A group's slot is its colour and its ground, so this is what keeps a map the reader has learnt from repainting. */
test("giving loose tags a named area leaves every neighbourhood the slot, and so the colour, it had", () => {
  const loose = ["rust", "zig"];
  const topics = nodes([...A, ...B, ...C, ...loose]);
  const slotsOf = (groups: DrawnGroup[]) =>
    new Map(groups.map((g) => [`${g.kind}:${g.anchor}`, g.slot]));

  const before = slotsOf(groupsFor(topics, LINKS, []));
  const after = slotsOf(groupsFor(topics, LINKS, [named("Systems", loose)]));

  const kept = [...after].filter(([key]) => key.startsWith("neighbourhood:"));
  expect(kept).toHaveLength(3);
  for (const [key, slot] of kept) {
    expect(slot).toBe(before.get(key));
  }
  expect(after.get("named:rust")).toBe(before.get("neighbourhood:rust"));
});

test("detection runs over the remainder, so no tag sits in two groups", () => {
  const groups = groupsFor(TOPICS, LINKS, [named("Streaming", A)]);
  const everyTag = groups.flatMap((g) => g.tags);

  expect(everyTag).toHaveLength(new Set(everyTag).size);
  expect(new Set(everyTag)).toStrictEqual(new Set([...A, ...B, ...C]));
});

test("a link into a named area does not pull the remainder together", () => {
  const hub = ["hub"];
  const topics = nodes([...hub, ...B, ...C]);
  const spokes = [...B, ...C].map((tag) => ({ a: "hub", b: tag, weight: 9 }));
  const links = [...clique(B), ...clique(C), ...spokes];

  const groups = groupsFor(topics, links, [named("Platform", hub)]);

  expect(groups.map((g) => g.tags.length)).toStrictEqual([6, 6, 1]);
});

test("a named area of one tag is still drawn, where a neighbourhood of one is not", () => {
  const groups = groupsFor(TOPICS, LINKS, [named("Queues", ["c1"])]);

  expect(groups.find((g) => g.kind === "named")).toMatchObject({
    name: "Queues",
    kind: "named",
    tags: ["c1"],
    anchor: "c1",
  });
});

test("a remainder that is one blob leaves the named areas standing alone", () => {
  const groups = groupsFor(TOPICS, LINKS, [
    named("Streaming", A, 1),
    named("Testing", B, 2),
  ]);

  expect(groups.map((g) => [g.name, g.kind])).toStrictEqual([
    ["Streaming", "named"],
    ["Testing", "named"],
  ]);
});

test("a named area keeps only the tags the map draws, and goes when it has none", () => {
  const groups = groupsFor(TOPICS, LINKS, [
    named("Gone", ["rust", "zig"], 1),
    named("Partly", ["a1", "rust"], 2),
  ]);

  expect(groups.filter((g) => g.kind === "named")).toStrictEqual([
    { tags: ["a1"], anchor: "a1", name: "Partly", kind: "named", slot: 3 },
  ]);
});

test("a named area is anchored on its most linked member, as a neighbourhood is", () => {
  const links = [
    { a: "x", b: "y", weight: 1 },
    { a: "y", b: "z", weight: 1 },
  ];

  const [group] = groupsFor(nodes(["x", "y", "z"]), links, [
    named("Chain", ["x", "y", "z"]),
  ]);

  expect(group!.anchor).toBe("y");
});

test("a named area is called by its own name at every length", () => {
  const names = groupNames({
    tags: ["kafka", "flink", "debezium"],
    anchor: "kafka",
    name: "Data in Motion",
    kind: "named",
  });

  expect(names).toStrictEqual({
    short: "Data in Motion",
    list: "Data in Motion",
    accessible: "Data in Motion, named area with 3 areas",
  });
});

test("a named area of one agrees its count", () => {
  expect(
    groupNames({ tags: ["kafka"], anchor: "kafka", name: "Q", kind: "named" })
      .accessible,
  ).toBe("Q, named area with 1 area");
});

test("a neighbourhood keeps the names detection gives it", () => {
  const group = { tags: ["kafka", "flink"], anchor: "kafka" };

  expect(
    groupNames({ ...group, name: "around kafka", kind: "neighbourhood" }),
  ).toStrictEqual(neighbourhoodNames(group));
});
