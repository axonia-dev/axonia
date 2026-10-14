/** How the map and its readings stand: side by side, the readings under the map, or the readings above it. */
export type MapLayout = "beside" | "under" | "above";

export interface AnswerPlace {
  /** The matched areas the readings panel answers with. */
  inPanel: readonly string[];
  /** The matched areas answered under the drawing, below the box. */
  underDrawing: readonly string[];
  /** Whether a chosen area's own people give way to the term's, so the page lists one set of people. */
  panelGivesWay: boolean;
}

const NONE: readonly string[] = [];

/**
 * Where a term's answer is listed, so it lands in sight of the box and the page lists people once. Beside the map the
 * panel answers. Stacked, the answer goes under the drawing; with the readings under the map, a chosen area's own
 * people give way to it, which moves nothing above the box. With the readings above the box, as on a phone, the panel
 * must not change while a term is typed, so a term naming the chosen area adds nothing: the panel already lists them.
 */
export function answerPlace({
  layout,
  matchedAreas,
  selectedTag,
}: Readonly<{
  layout: MapLayout;
  matchedAreas: readonly string[];
  selectedTag: string | null;
}>): AnswerPlace {
  const matched = matchedAreas.length > 0;
  if (layout === "beside") {
    return {
      inPanel: matchedAreas,
      underDrawing: NONE,
      panelGivesWay: matched,
    };
  }
  if (layout === "under") {
    return {
      inPanel: NONE,
      underDrawing: matchedAreas,
      panelGivesWay: matched,
    };
  }
  const repeats =
    selectedTag !== null &&
    matchedAreas.length === 1 &&
    matchedAreas.includes(selectedTag);
  return {
    inPanel: NONE,
    underDrawing: repeats ? NONE : matchedAreas,
    panelGivesWay: false,
  };
}
