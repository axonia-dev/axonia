import { describe, expect, it } from "vitest";

import type { ReactionTally } from "@/features/recognition/domain/reaction";
import {
  reactionStrip,
  VISIBLE_REACTIONS,
} from "@/features/recognition/domain/reaction-strip";

function tallies(count: number): ReactionTally[] {
  return Array.from({ length: count }, (_, i) => ({
    kind: "EMOJI" as const,
    emoji: String.fromCodePoint(0x1f600 + i),
    definitionId: null,
    name: null,
    imageUrl: null,
    count: 1,
    viewerHasReacted: false,
  }));
}

describe("reactionStrip", () => {
  it("draws everything while the row can hold it", () => {
    const strip = reactionStrip(tallies(VISIBLE_REACTIONS));

    expect(strip.visible).toHaveLength(VISIBLE_REACTIONS);
    expect(strip.overflow).toEqual([]);
  });

  it("draws one past the limit rather than trading it for a control of the same width", () => {
    const strip = reactionStrip(tallies(VISIBLE_REACTIONS + 1));

    expect(strip.visible).toHaveLength(VISIBLE_REACTIONS + 1);
    expect(strip.overflow).toEqual([]);
  });

  it("collapses once there is more than one chip's worth to gain", () => {
    const strip = reactionStrip(tallies(VISIBLE_REACTIONS + 2));

    expect(strip.visible).toHaveLength(VISIBLE_REACTIONS);
    expect(strip.overflow).toHaveLength(2);
  });

  // Pinned in literals rather than through the constant, which every other case here is written
  // against and so cannot notice it moving. Four is what a slot card's row holds beside love and
  // the add control now that the card states its reactions outright instead of folding them away.
  it("draws four chips and sends the rest behind the overflow control", () => {
    const strip = reactionStrip(tallies(6));

    expect(strip.visible).toHaveLength(4);
    expect(strip.overflow).toHaveLength(2);
  });

  it("keeps the server's order across the split, adding no ranking of its own", () => {
    const all = tallies(20);

    const strip = reactionStrip(all);

    expect([...strip.visible, ...strip.overflow]).toEqual(all);
  });

  it("holds nothing when nothing has been placed", () => {
    expect(reactionStrip([])).toEqual({ visible: [], overflow: [] });
  });
});
