import type { MapContribution } from "@/features/expertise/domain/expertise";

export type Glyph = "mic" | "doc" | "question" | "check";

/**
 * Below this radius no icon survives: at five pixels a microphone and a page are the same blob. The fallback below is
 * four shapes chosen for staying distinguishable, not for meaning anything; the legend teaches the icon and zoom
 * delivers it.
 */
export const LOD_ICON_PX = 7;

const GLYPHS: Record<MapContribution["evidence"]["sourceType"], Glyph> = {
  SLOT: "mic",
  ARTIFACT: "doc",
  TOPIC: "question",
  ACTION_ITEM: "check",
};

export function glyphFor(
  sourceType: MapContribution["evidence"]["sourceType"],
): Glyph {
  return GLYPHS[sourceType];
}

export type Detail = "icon" | "shape";

export function detailFor(radiusPx: number): Detail {
  return radiusPx >= LOD_ICON_PX ? "icon" : "shape";
}
