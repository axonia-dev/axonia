import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  streamLabels,
  terrainLabels,
} from "@/features/expertise/ui/map-labels";
import { placeLabels } from "@/features/expertise/ui/map-labels-placement";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { stack, streamPointOf } from "@/features/expertise/ui/stream-layout";
import type { Spot } from "@/features/expertise/ui/terrain-projection";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

const size = { width: 800, height: 600 };

function topic(tag: string, x: number, y: number): PlacedTopic {
  return { tag, x, y, r: 10, declarations: 0, practised: true };
}

function mark(tag: string, slot: number, signalId: number): PlacedContribution {
  return {
    signalId,
    tag,
    x: 0,
    y: 0,
    slot,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: signalId,
    reach: null,
  };
}

/** Three areas, "kafka" the anchor of the pair and "rust" a neighbourhood of one. */
const TOPICS = [
  topic("kafka", 100, 100),
  topic("flink", 300, 100),
  topic("rust", 500, 100),
];
const GROUPS: DrawnGroup[] = [
  {
    tags: ["kafka", "flink"],
    anchor: "kafka",
    name: "around kafka",
    kind: "neighbourhood",
    slot: 0,
  },
  {
    tags: ["rust"],
    anchor: "rust",
    name: "around rust",
    kind: "neighbourhood",
    slot: 1,
  },
];
const MARKS = [
  mark("kafka", 0, 1),
  mark("kafka", 0, 2),
  mark("flink", 0, 3),
  mark("rust", 1, 4),
];

/** The identity camera: a layout point is its own spot, scaled into box fractions. */
function straight(x: number, y: number): Spot {
  return { left: x / size.width, top: y / size.height };
}

function textsOf(labels: ReturnType<typeof placeLabels>): string[] {
  return labels.map((placed) => placed.label.text);
}

describe("terrainLabels", () => {
  it("names every area something has landed in", () => {
    const names = terrainLabels(TOPICS, GROUPS, MARKS).map((l) => l.text);

    expect(names).toContain("flink");
    expect(names).toContain("rust");
  });

  // An area carried only by declarations is open water with a dashed ring over it, and an unnamed ring around
  // nothing is a circle a reader cannot account for.
  it("names an area nothing has landed in too, and ranks it last", () => {
    const named = terrainLabels(
      [...TOPICS, topic("offered-only", 700, 100)],
      GROUPS,
      MARKS,
    );

    expect(named.map((l) => l.text)).toContain("offered-only");
    expect(named.at(-1)!.text).toBe("offered-only");
  });

  /** A neighbourhood's name belongs on its region, and a region is the ground the marks made. */
  it("never seats a headline on an area nothing has landed in", () => {
    const seats = terrainLabels(
      [...TOPICS, topic("offered-only", 700, 100)],
      [{ ...GROUPS[0]!, tags: ["kafka", "flink", "offered-only"] }],
      MARKS,
    ).find((l) => l.kind === "neighbourhood")!.at;

    expect(seats.flatMap((seat) => seat.tags)).not.toContain("offered-only");
  });

  it("gives a neighbourhood a headline of its own", () => {
    expect(terrainLabels(TOPICS, GROUPS, MARKS).map((l) => l.text)).toContain(
      "AROUND KAFKA",
    );
  });

  /** A neighbourhood of one is its own area, and its headline would only repeat the name under it. */
  it("does not headline a neighbourhood of one", () => {
    expect(
      terrainLabels(TOPICS, GROUPS, MARKS).map((l) => l.text),
    ).not.toContain("AROUND RUST");
  });

  it("stands a headline on its anchor first of all", () => {
    const headline = terrainLabels(TOPICS, GROUPS, MARKS).find(
      (l) => l.kind === "neighbourhood",
    )!;

    expect(headline.at[0]).toStrictEqual({ x: 100, y: 100, tags: ["kafka"] });
  });

  // The anchor turns round the back of the globe like anything else, and a region with no headline on it is the
  // very thing the names were added to fix.
  it("offers a headline the rest of its neighbourhood to fall back on", () => {
    const headline = terrainLabels(TOPICS, GROUPS, MARKS).find(
      (l) => l.kind === "neighbourhood",
    )!;

    expect(headline.at.map((seat) => seat.tags)).toStrictEqual([
      ["kafka"],
      ["flink"],
    ]);
  });

  it("ranks headlines above areas, and the busiest area above the rest", () => {
    const ranked = terrainLabels(TOPICS, GROUPS, MARKS);

    expect(ranked[0]!.kind).toBe("neighbourhood");
    expect(ranked[1]!.text).toBe("kafka");
  });
});

describe("streamLabels", () => {
  const history = [
    mark("kafka", 0, 1),
    mark("kafka", 0, 2),
    mark("kafka", 0, 3),
    mark("flink", 0, 4),
    mark("flink", 0, 5),
  ];
  const layout = {
    bins: 3,
    from: 1,
    to: 5,
    bands: [
      { tag: "kafka", slot: 0, values: [3, 2, 1] },
      { tag: "flink", slot: 0, values: [1, 2, 3] },
    ],
  };
  const stacked = stack(layout, size);

  it("names each band and headlines the run they share", () => {
    expect(
      streamLabels(stacked, GROUPS, size).map((l) => l.text),
    ).toStrictEqual(["AROUND KAFKA", "flink", "kafka"]);
  });

  it("stands a headline across every band of its neighbourhood", () => {
    const headline = streamLabels(stacked, GROUPS, size)[0]!;

    expect(headline.at[0]!.tags).toStrictEqual(["kafka", "flink"]);
  });

  /** A band with no thickness has no room to hold a word, and a name over it names nothing a reader can see. */
  it("leaves a band too thin to hold a word unnamed", () => {
    const thin = stack(
      {
        ...layout,
        bands: [...layout.bands, { tag: "css", slot: 1, values: [0, 0, 0] }],
      },
      size,
    );

    expect(streamLabels(thin, GROUPS, size).map((l) => l.text)).not.toContain(
      "css",
    );
  });

  /** A history is often thickest at its newest edge, where a word centred on it runs out of the box. */
  it("falls back to the next thickest point when the thickest has no room for the name", () => {
    const rising = stack(
      {
        ...layout,
        bands: [
          { tag: "kafka", slot: 0, values: [1, 20, 40] },
          { tag: "flink", slot: 0, values: [1, 20, 40] },
        ],
      },
      size,
    );

    const placed = placeLabels(
      streamLabels(rising, GROUPS, size),
      streamPointOf(size, { zoom: 1, pan: { x: 0, y: 0 } }),
      size,
    );

    expect(placed.map((p) => p.label.text)).toContain("AROUND KAFKA");
  });

  it("has nothing to name when nothing has been shared", () => {
    expect(streamLabels([], GROUPS, size)).toStrictEqual([]);
    expect(history).toHaveLength(5);
  });
});

describe("placeLabels", () => {
  const labels = terrainLabels(TOPICS, GROUPS, MARKS);

  it("places a name through the very camera the drawing was painted with", () => {
    const [first] = placeLabels(labels, straight, size);

    expect(first!.x).toBe(100);
    expect(first!.label.text).toBe("AROUND KAFKA");
  });

  /** An area's name stands clear of the marks piled on the area itself. */
  it("lifts an area's name off the ground it names", () => {
    const flink = placeLabels(labels, straight, size).find(
      (placed) => placed.label.text === "flink",
    )!;

    expect(flink.y).toBeLessThan(100);
  });

  it("drops a name the camera is not pointed at", () => {
    expect(placeLabels(labels, () => null, size)).toStrictEqual([]);
  });

  it("drops a name that would run off the edge of the drawing", () => {
    expect(
      placeLabels(labels, () => ({ left: 0.999, top: 0.5 }), size),
    ).toStrictEqual([]);
  });

  // A map that crams every word in is unreadable, and a name nudged off the thing it names is worse than no name.
  it("drops a name that has nowhere clear to stand, rather than moving it", () => {
    const stacked = placeLabels(labels, () => ({ left: 0.5, top: 0.5 }), size);

    expect(stacked).toHaveLength(1);
    expect(stacked[0]!.label.kind).toBe("neighbourhood");
  });

  it("takes the next seat a headline offered when the first is taken", () => {
    const onlyFlink = (x: number, y: number) =>
      x === 300 && y === 100 ? straight(x, y) : null;
    const headline = placeLabels(labels, onlyFlink, size)[0]!;

    expect(textsOf([headline])).toStrictEqual(["AROUND KAFKA"]);
    expect(headline.seat.tags).toStrictEqual(["flink"]);
  });
});
