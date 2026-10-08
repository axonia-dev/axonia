import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Spot } from "@/features/expertise/ui/terrain-projection";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * How far from a mark a pointer still counts as aimed at it, as a multiple of the size the mark draws at, with a
 * floor so that a small mark is not a smaller target than a finger can hit.
 */
const REACH_PER_PX = 1.5;
const MIN_REACH_PX = 14;

export function reachFor(markPx: number): number {
  return Math.max(MIN_REACH_PX, markPx * REACH_PER_PX);
}

/**
 * The mark a pointer is aimed at: the nearest one within reach, and nothing when the pointer is over open ground.
 *
 * Nearest-wins is what makes a crowded area usable. The focusable overlay beside the drawing can only give a mark a
 * button where a whole undisturbed target fits, which is the right rule for a tab sequence and the wrong one for a
 * pointer: it leaves a reader zoomed in on two marks a few pixels apart able to reach neither, because each one
 * disturbs the other. Here the two share the ground between them and the reader picks by aiming closer.
 *
 * It reads the same projection the drawing was painted with, so what the pointer picks is what the eye sees.
 */
export interface Aim {
  marks: PlacedContribution[];
  spotOf: (mark: PlacedContribution) => Spot | null;
  size: GraphSize;
  /** Where the pointer is, in the box's own pixels. */
  at: { x: number; y: number };
  reach: number;
}

export function nearestMark(aim: Readonly<Aim>): PlacedContribution | null {
  const { marks, spotOf, size, at, reach } = aim;
  let best: PlacedContribution | null = null;
  let bestDistance = reach;

  for (const mark of marks) {
    const spot = spotOf(mark);
    if (spot === null) continue;
    const distance = Math.hypot(
      spot.left * size.width - at.x,
      spot.top * size.height - at.y,
    );
    if (distance <= bestDistance) {
      best = mark;
      bestDistance = distance;
    }
  }
  return best;
}
