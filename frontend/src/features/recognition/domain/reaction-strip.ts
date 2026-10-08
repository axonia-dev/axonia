import type { ReactionTally } from "@/features/recognition/domain/reaction";

/**
 * How many chips a contribution draws before the rest move behind one control.
 *
 * Four, everywhere. A slot card now states its reactions outright rather than folding them behind
 * a summary line, so the strip is drawn on every talk on the page at once and its width is spent
 * on every one of them; four still leaves the card's action row on one line beside love and the
 * add control, and reads as a sample rather than as a wall. The library and the community feed
 * take the same number: one rule for the whole product beats a per-surface count nobody can
 * predict from a glance.
 */
export const VISIBLE_REACTIONS = 4;

export interface ReactionStrip {
  visible: ReactionTally[];
  overflow: ReactionTally[];
}

/**
 * The chips drawn on the row, and the ones behind the overflow control.
 *
 * A ninth reaction is drawn rather than hidden: a "+1" occupies the width of the chip it
 * replaces and costs a press to read, so collapsing begins only once there is something to
 * gain. The order is the server's, which already puts the ones several members placed at the
 * head and breaks ties on which was placed first; nothing here reorders it, so what stays on
 * the row is what the strip was going to draw first anyway.
 */
export function reactionStrip(
  tallies: readonly ReactionTally[],
): ReactionStrip {
  if (tallies.length <= VISIBLE_REACTIONS + 1) {
    return { visible: [...tallies], overflow: [] };
  }
  return {
    visible: tallies.slice(0, VISIBLE_REACTIONS),
    overflow: tallies.slice(VISIBLE_REACTIONS),
  };
}
