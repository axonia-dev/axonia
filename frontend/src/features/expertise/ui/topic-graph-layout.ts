import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { compareTags } from "@/features/expertise/ui/compare-tags";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { markRadius } from "@/features/expertise/ui/ring-radius";
import { anchors, recentre } from "@/features/expertise/ui/topic-graph-framing";

export interface PlacedTopic {
  tag: string;
  x: number;
  y: number;
  r: number;
  /** People open to questions on the area. The drawing turns this into a ring; the layout only reserves room for it. */
  declarations: number;
  /** False for an area carried only by declarations: it has no circle, so its ring is the node itself. */
  practised: boolean;
}

export interface Body extends PlacedTopic {
  vx: number;
  vy: number;
  /** The node's real footprint: its ring when it has one, else its circle. */
  outer: number;
  ax: number;
  ay: number;
  slot: number;
}

const PASSES = 900;
/** Extra passes of {@link separate} alone, after the main loop, with none of its competing
 *  forces. See {@link declutter}. */
const DECLUTTER_PASSES = 150;
/** Clear air kept between two nodes' outermost marks. The layout once reserved a long tag's TEXT here, from when
 *  every node was drawn with its label beside it; the terrain draws no such label, and `map-labels.ts` culls the
 *  names it does draw in screen space. Left in, it held members of one neighbourhood about a hundred pixels apart,
 *  which no pull toward their group could close, so a neighbourhood never settled into one region. */
const GUTTER = 8;
/** Pull toward the group's own running centre, which is what makes a neighbourhood a region rather than a tendency:
 *  {@link anchors} only says where a group starts out, and a node pushed off by repulsion never came back. */
const COHESION = 0.06;
const TO_ANCHOR = 0.03;
/** Caps a very popular topic's circle: trades the size signal at the top end for a node that still fits a normal canvas. */
const MAX_RADIUS = 44;
/** Topic count the pass budget is tuned for; below it PASSES is untouched, above it passes scale down so total O(passes * n^2) work stays roughly constant. */
const REFERENCE_TOPIC_COUNT = 40;
const MIN_PASSES = 150;

/** Deterministic pseudo-random start; Math.random would move the map between two loads of identical data. */
function seeded(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 16807) % 2147483647;
    return s / 2147483647;
  };
}

/** Scales the pass budget down for large graphs so a 200-topic map doesn't freeze the tab; small graphs still get the full PASSES to settle. */
function passesFor(topicCount: number): number {
  return Math.max(
    MIN_PASSES,
    Math.round(PASSES * Math.min(1, REFERENCE_TOPIC_COUNT / topicCount)),
  );
}

function span(body: Body): number {
  return body.outer + GUTTER;
}

function separate(a: Body, b: Body): void {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const reach = span(a) + span(b);
  const ox = reach - Math.abs(dx);
  const oy = reach - Math.abs(dy);
  if (ox <= 0 || oy <= 0) return;
  if (ox < oy) {
    const push = Math.sign(dx || 1) * ox * 0.48;
    a.vx -= push;
    b.vx += push;
  } else {
    const push = Math.sign(dy || 1) * oy * 0.48;
    a.vy -= push;
    b.vy += push;
  }
}

/** Recomputed every pass rather than once: a group's centre is where its members are now, not where they started. */
function centroids(list: Body[]): Map<number, { x: number; y: number }> {
  const sums = new Map<number, { x: number; y: number; n: number }>();
  for (const body of list) {
    const sum = sums.get(body.slot) ?? { x: 0, y: 0, n: 0 };
    sums.set(body.slot, {
      x: sum.x + body.x,
      y: sum.y + body.y,
      n: sum.n + 1,
    });
  }
  return new Map(
    [...sums].map(([index, sum]) => [
      index,
      { x: sum.x / sum.n, y: sum.y / sum.n },
    ]),
  );
}

/** Clamps to [loMargin, extent - hiMargin]; falls back to the midpoint when that span is inverted, i.e. a body larger than the canvas. */
function clampAxis(
  value: number,
  loMargin: number,
  hiMargin: number,
  extent: number,
): number {
  const hi = extent - hiMargin;
  if (loMargin > hi) return extent / 2;
  return Math.max(loMargin, Math.min(hi, value));
}

/** Builds one body per topic, in canonicalTag order so placement never depends on the caller's array order. */
interface Start {
  topics: TopicNode[];
  anchorOf: Map<string, { x: number; y: number }>;
  groupOf: Map<string, number>;
  size: { width: number; height: number };
  random: () => number;
}

function buildBodies({
  topics,
  anchorOf,
  groupOf,
  size,
  random,
}: Start): Map<string, Body> {
  const { width, height } = size;
  const bodies = new Map<string, Body>();
  const sorted = [...topics].sort((a, b) =>
    compareTags(a.canonicalTag, b.canonicalTag),
  );
  for (const topic of sorted) {
    // Square root keeps the size signal alive across realistic practitioner counts instead of saturating at 6.
    const r = Math.min(MAX_RADIUS, 11 + Math.sqrt(topic.practitionerCount) * 6);
    const declarations = topic.openToQuestionsCount;
    const practised = topic.practitionerCount > 0;
    // Spacing, clamping and the label all measure from the outermost mark the node can paint: the circle
    // is not the node's footprint once a dashed ring, and its selection mark, sit outside it.
    const outer = markRadius(r, declarations, { hasPractitioners: practised });
    const anchor = anchorOf.get(topic.canonicalTag) ?? {
      x: width / 2,
      y: height / 2,
    };
    bodies.set(topic.canonicalTag, {
      tag: topic.canonicalTag,
      r,
      declarations,
      practised,
      outer,
      slot: groupOf.get(topic.canonicalTag) ?? -1,
      x: anchor.x + (random() - 0.5) * 100,
      y: anchor.y + (random() - 0.5) * 100,
      vx: 0,
      vy: 0,
      ax: anchor.x,
      ay: anchor.y,
    });
  }
  return bodies;
}

/**
 * A closing sweep of {@link separate} alone, run after the main simulation settles. In the main
 * loop that force competes every pass against repulsion, springs and the pull toward a group
 * anchor; a dense enough graph (many links relative to topic count) can settle into a stable
 * balance of those forces that still leaves a pair of labels touching, not because collision
 * response failed but because it never got a pass free of a fight to actually resolve it in.
 * Isolated from the other forces, a modest number of extra passes reliably clears whatever the
 * main loop left behind.
 */
function declutter(
  list: Body[],
  size: { width: number; height: number },
): void {
  const { width, height } = size;
  for (let pass = 0; pass < DECLUTTER_PASSES; pass++) {
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        separate(list[i]!, list[j]!);
      }
    }
    for (const body of list) {
      body.x += body.vx;
      body.y += body.vy;
      body.vx = 0;
      body.vy = 0;
      body.x = clampAxis(body.x, span(body), span(body), width);
      body.y = clampAxis(body.y, span(body), span(body), height);
    }
  }
}

/**
 * A small force layout: repulsion between every pair, springs along the links, a pull toward each topic's group
 * anchor and toward its group's running centre, and a separation that keeps every node's outermost mark clear of
 * every other's.
 */
export function layoutTopics(
  topics: TopicNode[],
  links: TopicLink[],
  groups: readonly Pick<DrawnGroup, "tags" | "slot">[],
  size: { width: number; height: number },
): PlacedTopic[] {
  const { width, height } = size;
  const anchorOf = anchors(groups, width, height);
  const random = seeded(42);
  const groupOf = new Map(
    groups.flatMap((group) =>
      group.tags.map((tag) => [tag, group.slot] as const),
    ),
  );
  const bodies = buildBodies({ topics, anchorOf, groupOf, size, random });

  const list = [...bodies.values()];
  const edges = links
    .map((l) => ({ a: bodies.get(l.a), b: bodies.get(l.b), w: l.weight }))
    .filter(
      (e): e is { a: Body; b: Body; w: number } =>
        e.a !== undefined && e.b !== undefined,
    );

  const passes = passesFor(list.length);
  for (let pass = 0; pass < passes; pass++) {
    const cool = 1 - pass / passes;
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        const a = list[i]!;
        const b = list[j]!;
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const d2 = dx * dx + dy * dy || 0.01;
        const d = Math.sqrt(d2);
        const f = 2100 / d2;
        a.vx -= (dx / d) * f;
        a.vy -= (dy / d) * f;
        b.vx += (dx / d) * f;
        b.vy += (dy / d) * f;
        separate(a, b);
      }
    }
    for (const e of edges) {
      const dx = e.b.x - e.a.x;
      const dy = e.b.y - e.a.y;
      const d = Math.sqrt(dx * dx + dy * dy) || 0.01;
      const f = (d - 88 / Math.sqrt(e.w)) * 0.045 * e.w;
      e.a.vx += (dx / d) * f;
      e.a.vy += (dy / d) * f;
      e.b.vx -= (dx / d) * f;
      e.b.vy -= (dy / d) * f;
    }
    const centre = centroids(list);
    for (const body of list) {
      const own = centre.get(body.slot) ?? { x: body.ax, y: body.ay };
      body.vx +=
        (body.ax - body.x) * TO_ANCHOR +
        (own.x - body.x) * COHESION +
        (width / 2 - body.x) * 0.002;
      body.vy +=
        (body.ay - body.y) * TO_ANCHOR +
        (own.y - body.y) * COHESION +
        (height / 2 - body.y) * 0.002;
      body.x += body.vx * cool * 0.5;
      body.y += body.vy * cool * 0.5;
      body.vx *= 0.8;
      body.vy *= 0.8;
      body.x = clampAxis(body.x, span(body), span(body), width);
      body.y = clampAxis(body.y, span(body), span(body), height);
    }
  }

  declutter(list, size);
  recentre(list, size);

  return list.map(({ tag, x, y, r, declarations, practised }) => ({
    tag,
    x,
    y,
    r,
    declarations,
    practised,
  }));
}
