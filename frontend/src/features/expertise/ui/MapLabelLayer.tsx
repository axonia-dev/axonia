import { isLit } from "@/features/expertise/ui/map-highlight";
import type { PlacedLabel } from "@/features/expertise/ui/map-labels-placement";

/** A word over a drawing has no background to sit on: an area's colour, a summit and open water all pass under it. */
const OUTLINE =
  "[text-shadow:0_1px_3px_var(--map-halo),0_0_2px_var(--map-halo)]";

/**
 * A stroke in the ground's contrast colour drawn under each letter, thick enough to close the gaps between tracked
 * capitals, where a mark would otherwise read as a letter. It follows the glyphs, so nothing wider than the word is
 * lightened or darkened.
 */
const THICK_OUTLINE =
  "[paint-order:stroke_fill] [-webkit-text-stroke:3px_var(--map-halo)] [text-shadow:0_0_2px_var(--map-halo),0_0_3px_var(--map-halo)]";

/**
 * The two kinds of headline differ in shape, not only in casing: a leader may well type a name in capitals. A named
 * area stands on a ruled plate, the way a sign someone put up would; a neighbourhood is lettered onto the ground, each
 * letter outlined in the ground's contrast colour. Either way the marks give way to the name.
 *
 * `map-labels-placement.ts` reserves each box without measuring it, so the plate's padding and both line heights are
 * its too.
 */
const KIND: Record<PlacedLabel["label"]["kind"], string> = {
  named:
    "font-display rounded-full border border-[color-mix(in_srgb,var(--map-ink)_45%,transparent)] bg-[var(--map-halo)] px-2 py-0.5 text-[12px] leading-4 font-semibold tracking-[0.02em]",
  neighbourhood: `font-display text-[11px] leading-4 font-semibold tracking-[0.16em] uppercase ${THICK_OUTLINE}`,
  area: `text-[11px] font-medium ${OUTLINE}`,
};

/**
 * The names over the drawing.
 *
 * `aria-hidden`, like the canvas under it: the panel beside the map lists every area in full and the overlay's
 * buttons already carry the name of every mark, so repeating the words here would only put a second, shorter copy of
 * the map in the reading order.
 *
 * The ink and its halo are the map's own, not the page's: the drawing has a palette of its own and it runs the
 * other way up between the two themes, so a name fixed to one of them is unreadable on the other.
 */
export function MapLabelLayer({
  labels,
  highlight,
}: Readonly<{
  labels: PlacedLabel[];
  highlight: ReadonlySet<string> | null;
}>) {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0">
      {labels.map(({ label, seat, x, y }) => (
        <span
          key={label.key}
          data-testid="map-label"
          data-kind={label.kind}
          data-lit={isLit(highlight, seat.tags)}
          style={{ left: x, top: y }}
          className={`text-map-ink absolute -translate-x-1/2 -translate-y-1/2 whitespace-nowrap ${KIND[label.kind]} ${
            isLit(highlight, seat.tags) ? "" : "opacity-35"
          }`}
        >
          {label.text}
        </span>
      ))}
    </div>
  );
}
