import type { MapHighlight } from "@/features/expertise/ui/map-highlight";
import type { MapSearch } from "@/features/expertise/ui/map-search";

export type MapTransition = "snap" | "fade" | "echo";

/** What the drawing shows, and how it gets there from what it showed before. */
export interface DrawnHighlight {
  highlight: MapHighlight;
  transition: MapTransition;
}

const matchedAnything = (found: MapSearch | null) =>
  found !== null && (found.tags.size > 0 || found.lit.size > 0);

/**
 * Only a settled term echoes. A highlight moved by the panel snaps, as it always has; undoing a search is not an
 * event, so clearing fades; and a term that matched nothing leaves the map as it was, fading back to it from a term
 * before, which may still be echoing.
 */
export function transitionFor({
  from,
  to,
  found,
  reduced,
}: Readonly<{
  from: string;
  to: string;
  found: MapSearch | null;
  reduced: boolean;
}>): MapTransition {
  if (from.trim() === to.trim()) return "snap";
  if (to.trim() === "") return "fade";
  if (!matchedAnything(found)) return from.trim() === "" ? "snap" : "fade";
  return reduced ? "fade" : "echo";
}
