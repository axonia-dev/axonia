import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { markRadius } from "@/features/expertise/ui/ring-radius";
import type { Extent } from "@/features/expertise/ui/terrain-camera";
import type {
  Body,
  PlacedTopic,
} from "@/features/expertise/ui/topic-graph-layout";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * A grid of group anchors whose last row is centred rather than left-aligned. Three neighbourhoods fill two of a
 * two-by-two grid's top cells and one of its bottom ones, so left-aligned they hang off the top-left corner and the
 * whole map is drawn into a quarter of its own box - on the globe, into a quarter that then faces away.
 *
 * A group's cell is its slot, not its place in the list, so a group that keeps its slot keeps its ground.
 */
export function anchors(
  groups: readonly Pick<DrawnGroup, "tags" | "slot">[],
  width: number,
  height: number,
): Map<string, { x: number; y: number }> {
  const map = new Map<string, { x: number; y: number }>();
  if (groups.length === 0) return map;
  const cells = Math.max(...groups.map((group) => group.slot)) + 1;
  const columns = Math.ceil(Math.sqrt(cells));
  const rows = Math.ceil(cells / columns);
  for (const group of groups) {
    const row = Math.floor(group.slot / columns);
    const inRow = Math.min(columns, cells - row * columns);
    const cx =
      (((group.slot % columns) + 0.5 + (columns - inRow) / 2) * width) /
      columns;
    const cy = ((row + 0.5) * height) / rows;
    for (const tag of group.tags) map.set(tag, { x: cx, y: cy });
  }
  return map;
}

/** A shift that carries the settled layout toward the middle without pushing either of its ends out of the box. */
function shiftWithin(
  shift: number,
  low: number,
  high: number,
  extent: number,
): number {
  return Math.max(-low, Math.min(extent - high, shift));
}

/**
 * The settled layout, carried bodily to the middle of the box.
 *
 * The anchors say where a neighbourhood starts and the forces decide where it ends up, and nothing in between owes
 * the box's centre anything: a graph with one dense neighbourhood settles wherever that neighbourhood pulled it.
 * Off-centre costs the flat reading half its room, and it costs the globe far more than that, because the middle of
 * the plane is the hemisphere the camera opens on - so an off-centre layout opens on open water.
 */
export function recentre(list: Body[], size: GraphSize): void {
  if (list.length === 0) return;
  const left = Math.min(...list.map((b) => b.x - b.outer));
  const right = Math.max(...list.map((b) => b.x + b.outer));
  const top = Math.min(...list.map((b) => b.y - b.outer));
  const bottom = Math.max(...list.map((b) => b.y + b.outer));

  const dx = shiftWithin(
    size.width / 2 - (left + right) / 2,
    left,
    right,
    size.width,
  );
  const dy = shiftWithin(
    size.height / 2 - (top + bottom) / 2,
    top,
    bottom,
    size.height,
  );
  for (const body of list) {
    body.x += dx;
    body.y += dy;
  }
}

/** Clear water kept around the land, so the map is framed rather than cropped to its own coastline. */
const SHORE_ROOM = 70;

/**
 * The part of the plane the map is actually drawn on: every area's outermost mark, and room for the coastline the
 * heightfield spreads around it. The whole plane where there is nothing to measure.
 */
export function drawnExtent(topics: PlacedTopic[], size: GraphSize): Extent {
  if (topics.length === 0) {
    return { minX: 0, maxX: size.width, minY: 0, maxY: size.height };
  }
  const reach = (topic: PlacedTopic) =>
    markRadius(topic.r, topic.declarations, {
      hasPractitioners: topic.practised,
    }) + SHORE_ROOM;

  return {
    minX: Math.min(...topics.map((topic) => topic.x - reach(topic))),
    maxX: Math.max(...topics.map((topic) => topic.x + reach(topic))),
    minY: Math.min(...topics.map((topic) => topic.y - reach(topic))),
    maxY: Math.max(...topics.map((topic) => topic.y + reach(topic))),
  };
}
