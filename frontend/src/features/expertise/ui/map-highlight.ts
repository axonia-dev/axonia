import type { Reading } from "@/features/expertise/ui/expertise-readings";
import type { MapSearch } from "@/features/expertise/ui/map-search";

/**
 * What the drawing is about, in its two channels.
 *
 * The ground, the names and the stream's bands are drawn per area and read {@link MapHighlight.tags}. A mark reads
 * {@link MapHighlight.marks} where a term picked marks out one by one, and follows its own area otherwise: only a
 * search can say "this talk, not the ground it stands on", and only a search ever needs to.
 */
export interface MapHighlight {
  /** The areas the map is about, or null when it is about all of them. */
  tags: ReadonlySet<string> | null;
  /** The marks a term picked out, or null when every mark follows its own area. */
  marks: ReadonlySet<number> | null;
}

/**
 * The areas the panel beside the map is pointing at, or null when it is pointing at the whole map.
 *
 * A reading that answers with nothing is null rather than an empty set: dimming every area on the map to say "no
 * area matched" leaves a reader staring at a drawing that looks broken, and the panel says it in words already.
 */
export function highlightFor(
  reading: Reading | null,
  selectedTag: string | null,
): ReadonlySet<string> | null {
  if (reading !== null) {
    return reading.rows.length === 0
      ? null
      : new Set(reading.rows.map((row) => row.tag));
  }
  return selectedTag === null ? null : new Set([selectedTag]);
}

/** Whether a thing on the drawing is what the panel is about. Everything is, when nothing in particular is. */
export function isLit(
  highlight: ReadonlySet<string> | null,
  tags: readonly string[],
): boolean {
  return highlight === null || tags.some((tag) => highlight.has(tag));
}

/**
 * What the drawing is about once a term is taken into account.
 *
 * A term answers instead of the panel rather than alongside it: intersected, the two go empty the moment a reader
 * searches inside a reading that lists three areas, and the map they were reading blanks under them.
 *
 * A term that matched nothing leaves the map exactly as it was. The count beside the box says "No matches" in
 * words, which a drawing dimmed end to end cannot say - it only looks broken.
 */
export function withSearch(
  tags: ReadonlySet<string> | null,
  found: MapSearch | null,
): MapHighlight {
  if (found === null || (found.tags.size === 0 && found.lit.size === 0)) {
    return { tags, marks: null };
  }
  return { tags: found.tags, marks: found.lit };
}

/** Whether a mark is what the map is about: what a term picked out, or, with no term, whatever its area is. */
export function markLit(
  highlight: MapHighlight,
  mark: Readonly<{ signalId: number; tag: string }>,
): boolean {
  return highlight.marks === null
    ? isLit(highlight.tags, [mark.tag])
    : highlight.marks.has(mark.signalId);
}
