import { expect, test } from "vitest";

import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import {
  detectNeighbourhoods,
  MIN_VOCABULARY,
  neighbourhoodNames,
} from "@/features/expertise/ui/topic-neighbourhoods";

function nodes(...tags: string[]): TopicNode[] {
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

const TWO_CLUSTERS: TopicLink[] = [
  { a: "debezium", b: "kafka", weight: 3 },
  { a: "kafka", b: "schema-registry", weight: 3 },
  { a: "debezium", b: "schema-registry", weight: 2 },
  { a: "tdd", b: "testing", weight: 3 },
  { a: "code-review", b: "testing", weight: 3 },
  { a: "code-review", b: "tdd", weight: 2 },
];
const SIX = nodes(
  "kafka",
  "debezium",
  "schema-registry",
  "testing",
  "tdd",
  "code-review",
);

// Below MIN_VOCABULARY; these tests exercise detection itself, not the floor.
const LOW_MIN = 2;

function clique(tags: string[]): TopicLink[] {
  const links: TopicLink[] = [];
  for (const [i, a] of tags.entries()) {
    for (const b of tags.slice(i + 1)) {
      const pair = [a, b].sort((x, y) => x.localeCompare(y));
      links.push({ a: pair[0]!, b: pair[1]!, weight: 1 });
    }
  }
  return links;
}

test("two densely linked groups are found as two neighbourhoods", () => {
  const found = detectNeighbourhoods(SIX, TWO_CLUSTERS, LOW_MIN);

  expect(found).toHaveLength(2);
  expect(found.map((n) => n.tags.length).sort((a, b) => a - b)).toEqual([3, 3]);
});

test("the anchor is the topic most linked to its own group, not the biggest", () => {
  const found = detectNeighbourhoods(SIX, TWO_CLUSTERS, LOW_MIN);
  const dataGroup = found.find((n) => n.tags.includes("kafka"));

  // kafka carries 6 of its group's weight; debezium and schema-registry carry 5 each
  expect(dataGroup?.anchor).toBe("kafka");
});

test("detection is deterministic across repeated runs on the same payload", () => {
  const once = JSON.stringify(detectNeighbourhoods(SIX, TWO_CLUSTERS, LOW_MIN));
  const twice = JSON.stringify(
    detectNeighbourhoods(SIX, TWO_CLUSTERS, LOW_MIN),
  );
  const thrice = JSON.stringify(
    detectNeighbourhoods([...SIX].reverse(), TWO_CLUSTERS, LOW_MIN),
  );

  expect(twice).toBe(once);
  expect(thrice).toBe(once);
});

test("an unlinked topic is its own neighbourhood and its own anchor", () => {
  const found = detectNeighbourhoods(
    [...SIX, ...nodes("accessibility")],
    TWO_CLUSTERS,
    LOW_MIN,
  );
  const alone = found.find((n) => n.tags.includes("accessibility"));

  expect(alone?.tags).toEqual(["accessibility"]);
  expect(alone?.anchor).toBe("accessibility");
});

test("a vocabulary below the floor yields no neighbourhoods at all", () => {
  const found = detectNeighbourhoods(nodes("kafka", "debezium"), [
    { a: "debezium", b: "kafka", weight: 1 },
  ]);

  expect(found).toEqual([]);
});

test("a partition that collapses to one group is suppressed at the floor and below it", () => {
  const aboveFloor = nodes(
    ...Array.from(
      { length: MIN_VOCABULARY + 1 },
      (_, i) => `n${String(i).padStart(2, "0")}`,
    ),
  );
  const links = clique(aboveFloor.map((t) => t.canonicalTag));

  // The default call clears the count guard on margin (13 > 12), so a [] result can only come from degeneracy.
  expect(detectNeighbourhoods(aboveFloor, links)).toEqual([]);
  // Passing LOW_MIN removes the count guard entirely, isolating the degeneracy rule as the sole cause of [].
  expect(detectNeighbourhoods(aboveFloor, links, LOW_MIN)).toEqual([]);
});

test("a large blob with one unlinked straggler is suppressed as nearly-degenerate", () => {
  const blob = Array.from(
    { length: MIN_VOCABULARY - 1 },
    (_, i) => `b${String(i).padStart(2, "0")}`,
  );
  const community = nodes(...blob, "straggler");

  const found = detectNeighbourhoods(community, clique(blob));

  expect(found).toEqual([]);
});

test("the three name forms all read from the anchor and the size", () => {
  const names = neighbourhoodNames({
    anchor: "kafka",
    tags: ["kafka", "debezium", "flink"],
  });

  expect(names.short).toBe("AROUND KAFKA");
  expect(names.list).toBe("around kafka");
  expect(names.accessible).toBe("kafka, and 2 areas alongside it");
});

test("the accessible name uses the singular topic for exactly one companion", () => {
  const names = neighbourhoodNames({
    anchor: "kafka",
    tags: ["kafka", "debezium"],
  });

  expect(names.accessible).toBe("kafka, and 1 area alongside it");
});

test("a lone neighbourhood's accessible name does not claim companions", () => {
  const names = neighbourhoodNames({
    anchor: "accessibility",
    tags: ["accessibility"],
  });

  expect(names.accessible).toBe("accessibility, with nothing else beside it");
});
