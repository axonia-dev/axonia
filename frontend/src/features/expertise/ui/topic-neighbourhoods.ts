import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { compareTags } from "@/features/expertise/ui/compare-tags";
import { plural } from "@/lib/plural";

export interface Neighbourhood {
  tags: string[];
  anchor: string;
}

/** Below this, a community's vocabulary is too small for the map to beat the plain topic cloud. */
export const MIN_VOCABULARY = 12;

/**
 * A partition whose largest group holds at least this share of the vocabulary is one blob, not a structure. 0.9 is a
 * first estimate pending real data; the exact floor is an open question.
 */
const DEGENERATE_SHARE = 0.9;

const MAX_PASSES = 20;

/** No groups are drawn when the partition itself is noise: nearly everything in one group, or every tag its own island. */
function isDegenerate(
  tagCount: number,
  grouped: Map<string, string[]>,
): boolean {
  const largest = Math.max(...[...grouped.values()].map((g) => g.length));
  return largest / tagCount >= DEGENERATE_SHARE || grouped.size === tagCount;
}

function neighbours(links: TopicLink[]): Map<string, Map<string, number>> {
  const map = new Map<string, Map<string, number>>();
  const add = (from: string, to: string, weight: number) => {
    const row = map.get(from) ?? new Map<string, number>();
    row.set(to, (row.get(to) ?? 0) + weight);
    map.set(from, row);
  };
  for (const link of links) {
    add(link.a, link.b, link.weight);
    add(link.b, link.a, link.weight);
  }
  return map;
}

/**
 * The neighbouring label carrying the most weight. Ties go to the incumbent label first (a challenger must strictly
 * exceed it), then to the alphabetically first challenger among the rest, so a run cannot drift.
 */
function bestLabel(
  tag: string,
  adjacency: Map<string, Map<string, number>>,
  labels: Map<string, string>,
): string {
  const weights = new Map<string, number>();
  for (const [other, weight] of adjacency.get(tag) ?? []) {
    const label = labels.get(other) ?? other;
    weights.set(label, (weights.get(label) ?? 0) + weight);
  }
  let best = labels.get(tag) ?? tag;
  let bestWeight = weights.get(best) ?? 0;
  for (const [label, weight] of [...weights].sort((x, y) =>
    compareTags(x[0], y[0]),
  )) {
    if (weight > bestWeight) {
      best = label;
      bestWeight = weight;
    }
  }
  return best;
}

/** Walks tags in a fixed alphabetical order each pass, iterated to a fixed point, so a run cannot drift. */
function propagateLabels(
  tags: string[],
  adjacency: Map<string, Map<string, number>>,
): Map<string, string> {
  const labels = new Map(tags.map((t) => [t, t] as const));

  for (let pass = 0; pass < MAX_PASSES; pass++) {
    let moved = false;
    for (const tag of tags) {
      const next = bestLabel(tag, adjacency, labels);
      if (next !== labels.get(tag)) {
        labels.set(tag, next);
        moved = true;
      }
    }
    if (!moved) break;
  }

  return labels;
}

function groupByLabel(
  tags: string[],
  labels: Map<string, string>,
): Map<string, string[]> {
  const grouped = new Map<string, string[]>();
  for (const tag of tags) {
    const label = labels.get(tag) ?? tag;
    grouped.set(label, [...(grouped.get(label) ?? []), tag]);
  }
  return grouped;
}

/**
 * Label propagation over the topic links. Deterministic on purpose: a random seed would change a
 * group's *name* between two loads of identical data. `minVocabulary` defaults to `MIN_VOCABULARY`; a caller with
 * the community's real floor value can override it (tests do, to exercise detection below the production floor).
 */
export function detectNeighbourhoods(
  topics: TopicNode[],
  links: TopicLink[],
  minVocabulary = MIN_VOCABULARY,
): Neighbourhood[] {
  if (topics.length < minVocabulary) return [];

  const tags = topics.map((t) => t.canonicalTag).sort(compareTags);
  const adjacency = neighbours(links);
  const labels = propagateLabels(tags, adjacency);
  const grouped = groupByLabel(tags, labels);

  if (isDegenerate(tags.length, grouped)) return [];

  return [...grouped.values()]
    .map((group) => ({ tags: group, anchor: anchorIn(group, adjacency) }))
    .sort(
      (a, b) =>
        b.tags.length - a.tags.length || compareTags(a.anchor, b.anchor),
    );
}

/** The member holding the group together: the most link weight to its *own* members, not the most practitioners. */
export function anchorOf(group: string[], links: TopicLink[]): string {
  return anchorIn(group, neighbours(links));
}

function anchorIn(
  group: string[],
  adjacency: Map<string, Map<string, number>>,
): string {
  const inside = new Set(group);
  let anchor = [...group].sort(compareTags)[0]!;
  let best = -1;
  for (const tag of [...group].sort(compareTags)) {
    let weight = 0;
    for (const [other, w] of adjacency.get(tag) ?? []) {
      if (inside.has(other)) weight += w;
    }
    if (weight > best) {
      anchor = tag;
      best = weight;
    }
  }
  return anchor;
}

/**
 * The three lengths a neighbourhood's name renders at. "Around" claims proximity, not subject, which is the only claim
 * the evidence supports: a group holding postgres is not *about* Kafka, it sits next to it.
 */
export function neighbourhoodNames(n: Neighbourhood): {
  short: string;
  list: string;
  accessible: string;
} {
  const others = n.tags.length - 1;
  const accessible =
    others === 0
      ? `${n.anchor}, with nothing else beside it`
      : `${n.anchor}, and ${plural(others, "area")} alongside it`;
  return {
    short: `AROUND ${n.anchor.toUpperCase()}`,
    list: `around ${n.anchor}`,
    accessible,
  };
}
