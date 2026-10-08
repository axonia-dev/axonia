import { expect, test } from "vitest";

import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { layoutTopics } from "@/features/expertise/ui/topic-graph-layout";

function node(
  canonicalTag: string,
  practitionerCount = 1,
  openToQuestionsCount = 0,
): TopicNode {
  return {
    canonicalTag,
    practitionerCount,
    signalCount: 1,
    topHolderShare: 1,
    topHolderName: "Someone",
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    lastActivityAt: "2026-08-01T00:00:00Z",
    openToQuestionsCount,
  };
}

const TOPICS = ["kafka", "debezium", "testing", "tdd", "flink"].map((t) =>
  node(t),
);
const LINKS: TopicLink[] = [
  { a: "debezium", b: "kafka", weight: 2 },
  { a: "tdd", b: "testing", weight: 2 },
];

test("every topic's whole circle stays inside the canvas, not just its center", () => {
  const placed = layoutTopics(TOPICS, LINKS, [], { width: 820, height: 610 });

  expect(placed).toHaveLength(5);
  for (const p of placed) {
    expect(p.x - p.r).toBeGreaterThanOrEqual(0);
    expect(p.x + p.r).toBeLessThanOrEqual(820);
    expect(p.y - p.r).toBeGreaterThanOrEqual(0);
    expect(p.y + p.r).toBeLessThanOrEqual(610);
  }
});

test("the layout is deterministic regardless of the input array's order", () => {
  const shuffled = [...TOPICS].reverse();
  const base = layoutTopics(TOPICS, LINKS, [], { width: 820, height: 610 });
  const fromShuffled = layoutTopics(shuffled, LINKS, [], {
    width: 820,
    height: 610,
  });

  expect(fromShuffled).toEqual(base);
});

test("radius grows with the practitioner count", () => {
  const placed = layoutTopics([node("small", 1), node("big", 6)], [], [], {
    width: 820,
    height: 610,
  });
  const small = placed.find((p) => p.tag === "small");
  const big = placed.find((p) => p.tag === "big");

  expect(big!.r).toBeGreaterThan(small!.r);
});

// A node's size is what the map promises never to inflate: a declaration is drawn, never
// measured. Forty people willing to be asked must leave the circle exactly where nobody
// willing leaves it.
test("people open to questions never change a node's radius, only practitioners do", () => {
  const placed = layoutTopics(
    [node("undeclared", 3, 0), node("declared", 3, 40)],
    [],
    [],
    { width: 820, height: 610 },
  );
  const undeclared = placed.find((p) => p.tag === "undeclared")!;
  const declared = placed.find((p) => p.tag === "declared")!;

  expect(declared.r).toBe(undeclared.r);
});

test("keeps a node's spacing to its drawn mark, whatever its tag is called", () => {
  const spacing = (tags: string[]) => {
    const placed = layoutTopics(
      tags.map((tag) => node(tag, 3)),
      [],
      [],
      { width: 820, height: 610 },
    );
    const [a, b] = placed;
    return Math.hypot(b!.x - a!.x, b!.y - a!.y);
  };

  expect(spacing(["a", "a-very-long-topic-name"])).toBeCloseTo(
    spacing(["a", "b"]),
    9,
  );
});

/** A neighbourhood is drawn as one region of ground, and a member left out on its own is open water with a name on
 *  it. Every member must end up nearer its own group than the groups are to each other. */
test("settles a neighbourhood into one cluster rather than a scatter", () => {
  const tags = ["k1", "k2", "k3", "r1", "r2", "r3"];
  const groups = [
    { tags: ["k1", "k2", "k3"], slot: 0 },
    { tags: ["r1", "r2", "r3"], slot: 1 },
  ];
  const placed = layoutTopics(
    tags.map((tag) => node(tag, 3)),
    [],
    groups,
    { width: 820, height: 610 },
  );
  const at = (tag: string) => placed.find((p) => p.tag === tag)!;
  const spread = (group: string[]) =>
    Math.max(
      ...group.flatMap((a) =>
        group.map((b) => Math.hypot(at(a).x - at(b).x, at(a).y - at(b).y)),
      ),
    );
  const apart = Math.hypot(at("k1").x - at("r1").x, at("k1").y - at("r1").y);

  expect(
    Math.max(spread(groups[0]!.tags), spread(groups[1]!.tags)),
  ).toBeLessThan(apart);
});

test("linked topics end up closer than unlinked ones", () => {
  const placed = layoutTopics(TOPICS, LINKS, [], { width: 820, height: 610 });
  const at = (tag: string) => placed.find((p) => p.tag === tag)!;
  const gap = (a: string, b: string) =>
    Math.hypot(at(a).x - at(b).x, at(a).y - at(b).y);

  expect(gap("kafka", "debezium")).toBeLessThan(gap("kafka", "flink"));
});

// layoutTopics sorts by canonicalTag, so "graphql" comes back first whatever order it went in.
test("a placed topic carries the declaration count and whether anyone practises it", () => {
  const placed = layoutTopics(
    [node("kafka", 4, 3), node("graphql", 0, 1)],
    [],
    [],
    { width: 600, height: 400 },
  );

  expect(placed).toMatchObject([
    { tag: "graphql", declarations: 1, practised: false },
    { tag: "kafka", declarations: 3, practised: true },
  ]);
});

test("a declaration ring is kept inside the canvas, not just the circle", () => {
  const placed = layoutTopics([node("graphql", 4, 2)], [], [], {
    width: 200,
    height: 160,
  });

  const p = placed[0]!;
  const outer = p.r + 8;
  expect(p.y).toBeGreaterThanOrEqual(outer + 16);
  expect(p.y).toBeLessThanOrEqual(160 - (outer + 26));
});
