import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  type DrawnGroup,
  groupNames,
} from "@/features/expertise/ui/named-area-grouping";
import { type Stacked, xOf } from "@/features/expertise/ui/stream-layout";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * A place a name would accept, and what it would be standing on there. The tags are what a reading lights the name
 * by, so a name is bright exactly when the ground under it is: a headline left white over a region a reading has
 * set aside points the reader at the one region the reading is not about.
 */
export interface LabelSeat {
  x: number;
  y: number;
  tags: string[];
}

/**
 * A name the drawing carries. Colour alone says a region exists; it cannot say what the region is about, and a reader
 * looking at five teals with no word on them is being asked to take the picture on trust.
 */
export interface MapLabel {
  key: string;
  text: string;
  kind: DrawnGroup["kind"] | "area";
  /** How much practice stands behind the name: names that cannot both fit are decided on it. */
  weight: number;
  /**
   * Where the name would stand, best first. A headline wants its anchor and will take the next busiest area of its
   * neighbourhood when the anchor has turned round the back of the globe, because a region with no headline on it is
   * the very thing the names were added to fix.
   */
  at: readonly LabelSeat[];
}

/** A band thinner than this has no room to hold a word, and a name floating over it names nothing a reader can see. */
const MIN_BAND_PX = 11;

/**
 * A neighbourhood of one is its own area, and its headline would only repeat the name under it. A named area of one
 * is headlined all the same: its name is what a person called it, not the area's own.
 */
function minimumOf(group: DrawnGroup): number {
  return group.kind === "named" ? 1 : 2;
}

function headlineKey(group: DrawnGroup): string {
  return `${group.kind === "named" ? "named" : "around"}:${group.anchor}`;
}

/** Named first, since a person wrote them. */
const RANK: Record<MapLabel["kind"], number> = {
  named: 0,
  neighbourhood: 1,
  area: 2,
};

/**
 * Headlines own the space, then the busiest area: the mockup's rule, and the only one that survives a dense map. A
 * named headline takes its seat before any detected one.
 */
function byRank(labels: MapLabel[]): MapLabel[] {
  const rank = (label: MapLabel) => RANK[label.kind];
  return [...labels].sort(
    (a, b) =>
      rank(a) - rank(b) || b.weight - a.weight || a.key.localeCompare(b.key),
  );
}

function countByTag(marks: PlacedContribution[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const mark of marks) {
    counts.set(mark.tag, (counts.get(mark.tag) ?? 0) + 1);
  }
  return counts;
}

/**
 * A neighbourhood is named over its anchor rather than over the middle of its members, because the middle of a
 * scattered group is open water and a headline floating on the sea names nothing a reader can see. Standing it on
 * the anchor costs that area its own name, which costs nothing: the headline already says it.
 */
function headline(
  group: DrawnGroup,
  placed: PlacedTopic[],
  counts: Map<string, number>,
): MapLabel | null {
  const inside = placed.filter((topic) => group.tags.includes(topic.tag));
  if (inside.length < minimumOf(group)) return null;

  const anchor = inside.filter((topic) => topic.tag === group.anchor);
  const rest = inside
    .filter((topic) => topic.tag !== group.anchor)
    .sort(
      (a, b) =>
        counts.get(b.tag)! - counts.get(a.tag)! || a.tag.localeCompare(b.tag),
    );

  return {
    key: headlineKey(group),
    text: groupNames(group).short,
    kind: group.kind,
    weight: inside.reduce((sum, t) => sum + counts.get(t.tag)!, 0),
    at: [...anchor, ...rest].map(({ x, y, tag }) => ({
      x,
      y,
      tags: [tag],
    })),
  };
}

/**
 * The names the terrain carries: one headline per neighbourhood, and the name of every area it draws.
 *
 * Every area, including one carried only by declarations. That area is open water with a dashed ring over it, and
 * an unnamed ring around nothing is a circle a reader cannot account for. It is named last, because it has no
 * practice behind it, so a practised area always takes the space first.
 *
 * Headlines are seated only on areas something has landed in: a neighbourhood's name belongs on its region, and a
 * region is the ground the marks made.
 */
export function terrainLabels(
  topics: PlacedTopic[],
  groups: DrawnGroup[],
  marks: PlacedContribution[],
): MapLabel[] {
  const counts = countByTag(marks);

  const areas = topics.map<MapLabel>((topic) => ({
    key: `area:${topic.tag}`,
    text: topic.tag,
    kind: "area",
    weight: counts.get(topic.tag) ?? 0,
    at: [{ x: topic.x, y: topic.y, tags: [topic.tag] }],
  }));
  const practised = topics.filter((topic) => counts.has(topic.tag));
  const headlines = groups.flatMap((group) => {
    const label = headline(group, practised, counts);
    return label === null ? [] : [label];
  });

  return byRank([...headlines, ...areas]);
}

interface Seat {
  bin: number;
  thickness: number;
  middle: number;
}

/**
 * Where a set of bands has room to hold a word, thickest first. The thickest point alone is often a history's newest
 * edge, where a name centred on it runs out of the box, so the next thickest stands behind it.
 */
function seatsOf(bands: Stacked[]): Seat[] {
  const seats: Seat[] = [];
  for (let bin = 0; bin < (bands[0]?.lower.length ?? 0); bin++) {
    const lower = Math.min(...bands.map((band) => band.lower[bin]!));
    const upper = Math.max(...bands.map((band) => band.upper[bin]!));
    if (upper - lower >= MIN_BAND_PX) {
      seats.push({
        bin,
        thickness: upper - lower,
        middle: (lower + upper) / 2,
      });
    }
  }
  return seats.sort((a, b) => b.thickness - a.thickness || a.bin - b.bin);
}

/** One name over a run of bands, or none when they have no room between them to hold a word. */
function seated(
  bands: Stacked[],
  bins: number,
  size: GraphSize,
  named: Omit<MapLabel, "weight" | "at">,
): MapLabel[] {
  const seats = seatsOf(bands);
  if (seats.length === 0) return [];
  const tags = bands.map((band) => band.tag);
  return [
    {
      ...named,
      weight: seats[0]!.thickness,
      at: seats.map(({ bin, middle }) => ({
        x: xOf(bin, bins, size),
        y: middle,
        tags,
      })),
    },
  ];
}

/** The names the stream carries. Neighbours are stacked together, so a headline spans a contiguous run of bands. */
export function streamLabels(
  stacked: Stacked[],
  groups: DrawnGroup[],
  size: GraphSize,
): MapLabel[] {
  const bins = stacked[0]?.lower.length ?? 0;

  const areas = stacked.flatMap((band) =>
    seated([band], bins, size, {
      key: `area:${band.tag}`,
      text: band.tag,
      kind: "area",
    }),
  );
  const headlines = [...new Set(stacked.map((band) => band.slot))].flatMap(
    (slot) => {
      const group = groups.find((candidate) => candidate.slot === slot);
      const bands = stacked.filter((band) => band.slot === slot);
      if (group === undefined || bands.length < minimumOf(group)) return [];
      return seated(bands, bins, size, {
        key: headlineKey(group),
        text: groupNames(group).short,
        kind: group.kind,
      });
    },
  );

  return byRank([...headlines, ...areas]);
}
