/** The gap between a node's circle and its declaration ring. */
const RING_GAP = 8;

interface Options {
  /** An area with no practitioners has no circle to sit outside of: the ring is the node. */
  hasPractitioners?: boolean;
}

/**
 * The radius the declaration ring draws at, or null when nobody has declared the area. Both the layout
 * and the drawing call this: the layout needs it to reserve room, the drawing to paint, and the two
 * drifting apart would push every label off its node.
 */
export function ringRadius(
  r: number,
  declarations: number,
  { hasPractitioners = true }: Options = {},
): number | null {
  if (declarations === 0) return null;
  return hasPractitioners ? r + RING_GAP : r;
}

/**
 * How far outside the dashed ring a declaration-only node's selection mark sits. It is the gap a dashed
 * ring itself keeps from a circle, so one node never shows two different gaps; at less than that the solid
 * stroke and the dashes merge into one thick ring and a selected declaration stops reading as declared.
 */
const SELECTION_RING_GAP = RING_GAP;

/**
 * The outermost radius a node can ever paint at, its selection mark included. The layout spaces by it and
 * the tag label clears it, so selecting a declaration-only node never draws a line through its own name.
 */
export function markRadius(
  r: number,
  declarations: number,
  options: Options = {},
): number {
  const ring = ringRadius(r, declarations, options);
  if (ring === null) return r;
  return options.hasPractitioners === false ? ring + SELECTION_RING_GAP : ring;
}
