import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  streamLabels,
  terrainLabels,
} from "@/features/expertise/ui/map-labels";
import { placeLabels } from "@/features/expertise/ui/map-labels-placement";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { stack } from "@/features/expertise/ui/stream-layout";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

const size = { width: 800, height: 600 };

function topic(tag: string, x: number, y: number): PlacedTopic {
  return { tag, x, y, r: 10, declarations: 0, practised: true };
}

function mark(tag: string, signalId: number): PlacedContribution {
  return {
    signalId,
    tag,
    x: 0,
    y: 0,
    slot: 0,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: signalId,
    reach: null,
  };
}

const TOPICS = [
  topic("kafka", 100, 100),
  topic("flink", 300, 100),
  topic("rust", 500, 100),
];
const MARKS = [mark("kafka", 1), mark("flink", 2), mark("rust", 3)];
const STREAMING: DrawnGroup = {
  tags: ["kafka", "flink"],
  anchor: "kafka",
  name: "Data in Motion",
  kind: "named",
  slot: 0,
};
const SYSTEMS: DrawnGroup = {
  tags: ["rust"],
  anchor: "rust",
  name: "Systems",
  kind: "named",
  slot: 1,
};

describe("a named area on the terrain", () => {
  it("is headlined by its own name, as the leader typed it", () => {
    const headline = terrainLabels(TOPICS, [STREAMING], MARKS)[0]!;

    expect(headline).toMatchObject({
      key: "named:kafka",
      text: "Data in Motion",
      kind: "named",
    });
  });

  // A person said it; a headline over one area still says something the area's own name does not.
  it("is headlined even when it holds a single area", () => {
    const texts = terrainLabels(TOPICS, [SYSTEMS], MARKS).map((l) => l.text);

    expect(texts).toContain("Systems");
  });

  // A name a person wrote is the stronger reason to be on screen, so it is placed before any detected headline.
  it("ranks ahead of every neighbourhood headline, however much heavier, and above every area", () => {
    const topics = [...TOPICS, topic("go", 700, 100)];
    const marks = [...MARKS, mark("go", 4), mark("go", 5)];
    const around: DrawnGroup = {
      tags: ["rust", "go"],
      anchor: "go",
      name: "around go",
      kind: "neighbourhood",
      slot: 1,
    };

    const kinds = terrainLabels(topics, [STREAMING, around], marks).map(
      (l) => l.kind,
    );

    expect(kinds).toStrictEqual([
      "named",
      "neighbourhood",
      "area",
      "area",
      "area",
      "area",
    ]);
  });
});

describe("a named area competing for a seat", () => {
  it("keeps its headline where a heavier neighbourhood's would stand on it", () => {
    const topics = [...TOPICS, topic("go", 110, 100), topic("zig", 120, 110)];
    const heavy = [4, 5, 6, 7, 8].map((id) => mark(id < 7 ? "go" : "zig", id));
    const around: DrawnGroup = {
      tags: ["go", "zig"],
      anchor: "go",
      name: "around go",
      kind: "neighbourhood",
      slot: 1,
    };
    const names = terrainLabels(
      topics,
      [STREAMING, around],
      [...MARKS, ...heavy],
    );

    const placed = placeLabels(
      names,
      (x, y) => ({ left: x / size.width, top: y / size.height }),
      size,
    ).map((p) => p.label.text);

    expect(placed).toContain("Data in Motion");
    expect(placed).not.toContain("AROUND GO");
  });
});

describe("a named area over time", () => {
  const stacked = stack(
    {
      bins: 3,
      from: 1,
      to: 5,
      bands: [
        { tag: "kafka", slot: 0, values: [3, 2, 1] },
        { tag: "flink", slot: 0, values: [1, 2, 3] },
        { tag: "rust", slot: 1, values: [2, 2, 2] },
      ],
    },
    size,
  );

  it("heads its run of bands with its own name, even a run of one", () => {
    const named = streamLabels(stacked, [STREAMING, SYSTEMS], size).filter(
      (l) => l.kind === "named",
    );

    expect(named.map((l) => l.text)).toHaveLength(2);
    expect(named.map((l) => l.text)).toEqual(
      expect.arrayContaining(["Data in Motion", "Systems"]),
    );
  });
});
