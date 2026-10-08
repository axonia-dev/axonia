import type { MapContribution } from "@/features/expertise/domain/expertise";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

export interface PlacedContribution {
  signalId: number;
  tag: string;
  x: number;
  y: number;
  slot: number;
  sourceType: MapContribution["evidence"]["sourceType"];
  userId: string;
  occurredAt: number;
  reach: number | null;
}

/** Spread grows with the square root of an area's population, so a deep area is wider rather than denser. */
const SPREAD_PER_MARK = 3.4;

/**
 * The ceiling on that growth comes from the layout rather than from a chosen number: an area may claim a little
 * under half the distance to its nearest neighbour, so two crowded neighbours' discs stay disjoint at any depth.
 * A fixed ceiling instead stops widening at a population every large community passes, and the area beyond it
 * saturates into an opaque disc where no level-of-detail rule can help.
 */
const NEIGHBOUR_SHARE = 0.45;

/**
 * A mark's position is a function of its signal id alone, so it survives a reload, a new contribution elsewhere, and
 * any change in the order the server happened to return. Math.random and array index would each break one of those.
 */
export function hash(seed: number): number {
  let h = Math.imul(seed ^ 0x9e3779b9, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

type Slotted = Pick<DrawnGroup, "tags" | "slot">;

/** An area in no group takes the slot after the last group's, so it never reads as part of one. */
function slotOf(groups: readonly Slotted[], tag: string): number {
  const group = groups.find((candidate) => candidate.tags.includes(tag));
  return group?.slot ?? Math.max(-1, ...groups.map((g) => g.slot)) + 1;
}

function spreadCapOf(topic: PlacedTopic, topics: PlacedTopic[]): number {
  let nearest = Number.POSITIVE_INFINITY;
  for (const other of topics) {
    if (other.tag === topic.tag) continue;
    nearest = Math.min(
      nearest,
      Math.hypot(other.x - topic.x, other.y - topic.y),
    );
  }
  return nearest * NEIGHBOUR_SHARE;
}

function spreadFor(count: number, topicRadius: number, cap: number): number {
  return Math.max(
    topicRadius,
    Math.min(cap, Math.sqrt(count) * SPREAD_PER_MARK),
  );
}

export function placeContributions(
  contributions: MapContribution[],
  topics: PlacedTopic[],
  groups: readonly Slotted[],
): PlacedContribution[] {
  const topicAt = new Map(
    topics.map(
      (t) => [t.tag, { topic: t, spreadCap: spreadCapOf(t, topics) }] as const,
    ),
  );

  const counts = new Map<string, number>();
  for (const c of contributions) {
    counts.set(c.canonicalTag, (counts.get(c.canonicalTag) ?? 0) + 1);
  }

  const placed: PlacedContribution[] = [];
  for (const c of contributions) {
    const entry = topicAt.get(c.canonicalTag);
    if (entry === undefined) continue;
    const { topic } = entry;

    const spread = spreadFor(
      counts.get(c.canonicalTag) ?? 1,
      topic.r,
      entry.spreadCap,
    );
    const angle = hash(c.signalId) * Math.PI * 2;
    // Square root of a uniform sample fills the disc evenly; the raw sample would crowd the centre.
    const radius = Math.sqrt(hash(c.signalId ^ 0x5f356495)) * spread;

    placed.push({
      signalId: c.signalId,
      tag: c.canonicalTag,
      x: topic.x + Math.cos(angle) * radius,
      y: topic.y + Math.sin(angle) * radius,
      slot: slotOf(groups, c.canonicalTag),
      sourceType: c.evidence.sourceType,
      userId: c.userId,
      occurredAt: Date.parse(c.evidence.occurredAt),
      reach: c.reach,
    });
  }
  return placed;
}
