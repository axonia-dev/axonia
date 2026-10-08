import { expect, test } from "vitest";

import type { TopicNode } from "@/features/expertise/domain/expertise";
import { buildReading } from "@/features/expertise/ui/expertise-readings";

const NOW = new Date("2026-08-22T00:00:00Z");

function topic(over: Partial<TopicNode> & { canonicalTag: string }): TopicNode {
  return {
    practitionerCount: 1,
    signalCount: 1,
    topHolderShare: 1,
    topHolderName: "Dana Krupa",
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    lastActivityAt: "2026-08-01T00:00:00Z",
    openToQuestionsCount: 0,
    ...over,
  };
}

/** An area carried only by declarations: no holder, no activity, and nothing for a reading to measure. */
function declarationOnly(
  canonicalTag: string,
  openToQuestionsCount = 1,
): TopicNode {
  return topic({
    canonicalTag,
    practitionerCount: 0,
    signalCount: 0,
    topHolderShare: 0,
    topHolderName: null,
    presentedCount: 0,
    lastActivityAt: null,
    openToQuestionsCount,
  });
}

test("concentration states how many areas rest on one person", () => {
  const topics = [
    topic({ canonicalTag: "flink", signalCount: 2 }),
    topic({
      canonicalTag: "kafka",
      practitionerCount: 3,
      signalCount: 8,
      topHolderShare: 0.5,
    }),
  ];

  const r = buildReading("concentrated", topics, NOW);

  expect(r.statement).toBe("1 of 2 areas rests on a single person.");
  expect(r.rows.map((x) => x.tag)).toEqual(["flink", "kafka"]);
  expect(r.rows[0]?.value).toBe("100% of 2 · Dana");
  expect(r.badgeOf("kafka")).toBe("50%");
});

test("concentration falls back to the leading share when nothing rests on one person", () => {
  const topics = [
    topic({
      canonicalTag: "kafka",
      practitionerCount: 3,
      signalCount: 8,
      topHolderShare: 0.625,
    }),
    topic({
      canonicalTag: "ddd",
      practitionerCount: 2,
      signalCount: 4,
      topHolderShare: 0.5,
    }),
  ];

  expect(buildReading("concentrated", topics, NOW).statement).toBe(
    "The most concentrated area is 63% one person's work.",
  );
});

test("concentration renders a share with no dangling separator when the area has no top holder", () => {
  const topics = [
    topic({
      canonicalTag: "kafka",
      signalCount: 8,
      topHolderShare: 0.5,
      topHolderName: null,
    }),
  ];

  expect(buildReading("concentrated", topics, NOW).rows[0]?.value).toBe(
    "50% of 8",
  );
});

test("recently active leads with the newest activity and names the current month plainly", () => {
  const topics = [
    topic({ canonicalTag: "kafka", lastActivityAt: "2026-08-20T00:00:00Z" }),
    topic({ canonicalTag: "ddd", lastActivityAt: "2026-05-01T00:00:00Z" }),
  ];

  const r = buildReading("recent", topics, NOW);

  expect(r.rows.map((x) => x.tag)).toEqual(["kafka", "ddd"]);
  expect(r.rows[0]?.value).toBe("this month");
  expect(r.rows[1]?.value).toBe("4 months ago");
  expect(r.statement).toBe("The most recent activity was this month.");
  expect(r.badgeOf("ddd")).toBe("4m");
});

test("recent shows five and says how many it did not, and badgeOf still measures the rest", () => {
  const topics = Array.from({ length: 7 }, (_, i) =>
    topic({
      canonicalTag: `t${i}`,
      lastActivityAt: `2026-0${i + 1}-01T00:00:00Z`,
    }),
  );

  const r = buildReading("recent", topics, NOW);

  expect(r.kind).toBe("rank");
  expect(r.rows).toHaveLength(5);
  expect(r.rows.map((x) => x.tag)).toEqual(["t6", "t5", "t4", "t3", "t2"]);
  expect(r.total).toBe(7);
  expect(r.badgeOf("t0")).toBe("8m");
  expect(r.badgeOf("not-a-topic")).toBeNull();
});

test("not yet presented calls an OFFERED signal a proposal, never an offer", () => {
  const topics = [
    topic({
      canonicalTag: "graphql",
      presentedCount: 0,
      contributedCount: 2,
      offeredCount: 1,
    }),
  ];

  const r = buildReading("unpresented", topics, NOW);

  expect(r.statement).toBe("1 area has not been presented yet.");
  expect(r.rows[0]?.value).toBe("2 artifacts, 1 proposal, no talk");
});

test("not yet presented shows only the bucket that holds something", () => {
  const topics = [
    topic({
      canonicalTag: "schema-registry",
      presentedCount: 0,
      contributedCount: 3,
      offeredCount: 0,
    }),
  ];

  expect(buildReading("unpresented", topics, NOW).rows[0]?.value).toBe(
    "3 artifacts, no talk",
  );
});

test("not yet presented orders by total unused material, artifacts and proposals together", () => {
  const topics = [
    topic({
      canonicalTag: "single",
      presentedCount: 0,
      contributedCount: 1,
      offeredCount: 0,
    }),
    topic({
      canonicalTag: "mixed",
      presentedCount: 0,
      contributedCount: 3,
      offeredCount: 2,
    }),
  ];

  expect(
    buildReading("unpresented", topics, NOW).rows.map((x) => x.tag),
  ).toEqual(["mixed", "single"]);
});

test("the unpresented badge sums both buckets when an area has artifacts and proposals, not just one of them", () => {
  const topics = [
    topic({
      canonicalTag: "mixed",
      presentedCount: 0,
      contributedCount: 3,
      offeredCount: 2,
      signalCount: 1,
    }),
  ];

  expect(buildReading("unpresented", topics, NOW).badgeOf("mixed")).toBe("5");
});

test("not yet presented inverts when everything with activity has been presented", () => {
  expect(
    buildReading("unpresented", [topic({ canonicalTag: "kafka" })], NOW)
      .statement,
  ).toBe("Every area with activity has been presented.");
});

// The map draws a declaration, and no reading measures one.
test.each(["concentrated", "unpresented", "recent"] as const)(
  "%s gives a declaration-only area no row and no badge",
  (reading) => {
    const topics = [
      topic({ canonicalTag: "kafka", lastActivityAt: "2026-08-20T00:00:00Z" }),
      declarationOnly("graphql", 3),
    ];

    const r = buildReading(reading, topics, NOW);

    expect(r.rows.map((x) => x.tag)).not.toContain("graphql");
    expect(r.badgeOf("graphql")).toBeNull();
  },
);

test("a map of nothing but declarations has nothing to read, in any reading", () => {
  const topics = [declarationOnly("graphql"), declarationOnly("grpc")];

  expect(buildReading("recent", topics, NOW).statement).toBe(
    "Nothing to read here yet.",
  );
  expect(buildReading("concentrated", topics, NOW).statement).toBe(
    "Nothing to read here yet.",
  );
  expect(buildReading("unpresented", topics, NOW).statement).toBe(
    "Nothing to read here yet.",
  );
});
