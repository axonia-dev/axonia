import type {
  MapContribution,
  MapPerson,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { normalizeTag } from "@/lib/normalize-tag";
import { plural } from "@/lib/plural";

/** Everything the drawing is made of, which is everything a term can be matched against. */
export interface MapContents {
  topics: TopicNode[];
  items: MapContribution[];
  people: ReadonlyMap<string, MapPerson>;
}

export interface MapSearch {
  /** Areas whose name matched. */
  tags: ReadonlySet<string>;
  /** Contributions whose own words matched: the title it was given, or the person who made it. */
  hits: ReadonlySet<number>;
  /** Every mark the drawing lights: the hits, and everything standing on a matched area. */
  lit: ReadonlySet<number>;
}

/**
 * What a term picks out of the drawing.
 *
 * Matched through {@link normalizeTag} on both sides, which is how a reader gets to type what the page shows them:
 * the names are stored dashed and lowercase, and "Kafka Streams" is what they read.
 *
 * A contribution with no title of its own is not a hit: its only words are its area's name, and an area matched by
 * name already lights every mark standing on it. Counted both ways, the line beside the box would report the same
 * marks twice under two different words.
 */
export function searchMap(
  term: string,
  { topics, items, people }: MapContents,
): MapSearch | null {
  const needle = normalizeTag(term);
  if (needle === "") return null;

  const matches = (text: string | null) =>
    text !== null && normalizeTag(text).includes(needle);

  const tags = new Set(
    topics.map((topic) => topic.canonicalTag).filter((tag) => matches(tag)),
  );
  const hits = new Set<number>();
  const lit = new Set<number>();

  for (const item of items) {
    const author = people.get(item.userId)?.displayName ?? null;
    if (matches(item.evidence.label) || matches(author))
      hits.add(item.signalId);
    if (hits.has(item.signalId) || tags.has(item.canonicalTag)) {
      lit.add(item.signalId);
    }
  }

  return { tags, hits, lit };
}

/**
 * What the box says it found, beside it.
 *
 * The drawing answers by setting everything else back, which cannot say "nothing matched" - a map dimmed end to end
 * reads as broken. This says it in words, and the two halves are counted apart because they are different things:
 * areas the term named, and contributions whose own title or author it named.
 */
export function matchSummary({ tags, hits }: MapSearch): string {
  const counted = [
    ...(tags.size === 0 ? [] : [plural(tags.size, "area")]),
    ...(hits.size === 0 ? [] : [plural(hits.size, "contribution")]),
  ];
  return counted.length === 0 ? "No matches" : counted.join(", ");
}
