import * as THREE from "three";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Tinted } from "@/features/expertise/ui/echo-blend";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import {
  ATLAS_ORDER,
  type AtlasTile,
  tileFor,
} from "@/features/expertise/ui/mark-atlas";
import { chosenInk, markInk, setBack } from "@/features/expertise/ui/mark-ink";
import { type Detail } from "@/features/expertise/ui/mark-lod";
import {
  layoutXy,
  type Linear,
  linearOf,
  markTint,
  type Shades,
} from "@/features/expertise/ui/mark-tints";

/** Re-exported so the renderer reads a mark's world geometry from one place: `MARK_LIFT` sits beside the camera's
 *  other world constants, which the overlay can read without pulling three.js into the entry chunk. */
export { MARK_LIFT } from "@/features/expertise/ui/terrain-camera";

/** Where a plane coordinate sits on the drawn surface. The renderer owns elevation; this module owns what is drawn. */
export type Place = (x: number, y: number) => THREE.Vector3;

type Glyphs = THREE.Points<THREE.BufferGeometry, THREE.PointsMaterial>;

/** The two passes one mark is drawn in: its rim, and the glyph over it. */
interface Pair {
  glyph: Glyphs;
  rim: Glyphs;
}

export interface MarkLayer {
  /** The glyphs themselves, one object per tile of the atlas, and the chosen mark's own last. */
  points: Glyphs[];
  /** The rim behind each of them, in the other ink. Kept apart so a caller can see there are two passes. */
  halos: Glyphs[];
  /** The one mark a reader chose, or null while nothing is. A handle on the pair already standing in the two arrays
   *  above, so a renderer that knows only about those still draws it. */
  chosen: Pair | null;
  /** The colours of every glyph and rim object but the chosen pair's, which is always drawn in the chosen ink. */
  tints: Tinted[];
  /** How big a mark is drawn is a fact about the camera, so the zoom sets it here rather than rebuilding the layer.
   *  It is the layer's own job because the rim is drawn wider than the glyph and only the layer knows by how much. */
  resize: (markPx: number) => void;
  dispose: () => void;
}

function byTile(
  marks: PlacedContribution[],
  detail: Detail,
): Map<AtlasTile, PlacedContribution[]> {
  const groups = new Map<AtlasTile, PlacedContribution[]>();
  for (const mark of marks) {
    const tile = tileFor(mark.sourceType, detail);
    const group = groups.get(tile);
    if (group === undefined) groups.set(tile, [mark]);
    else group.push(mark);
  }
  return groups;
}

function tileTexture(
  atlas: THREE.CanvasTexture,
  tile: AtlasTile,
): THREE.Texture {
  const map = atlas.clone();
  map.needsUpdate = true;
  map.repeat.set(1 / ATLAS_ORDER.length, 1);
  map.offset.set(ATLAS_ORDER.indexOf(tile) / ATLAS_ORDER.length, 0);
  return map;
}

export interface Look {
  atlas: THREE.CanvasTexture;
  /** Which of the two glyph sets to draw. It changes at one threshold, so the layer survives most of a zoom. */
  detail: Detail;
  /**
   * The size a mark draws at, in CSS pixels, as it stands when the layer is built. It is a starting value and not
   * a fixed one: the camera resizes the material as the zoom changes rather than rebuilding the layer for it.
   */
  markPx: number;
  /**
   * The renderer's own pixel ratio. `gl_PointSize` counts device pixels, so a point given its size in CSS pixels
   * draws at half of it on a retina screen; multiplying it back out here is what keeps {@link Look.markPx} the
   * number a reader actually sees.
   */
  pixelRatio: number;
  /** The signal id of the mark the strip below the drawing is describing, if it is still on the map. */
  chosen?: number;
}

/**
 * One `Points` per tile, never one for everything: a `PointsMaterial` samples its whole map and the offset that picks
 * a tile lives on the texture, so a single object can only ever show one glyph. The clones share the image, which
 * makes this eight materials over one upload.
 */
/** How much wider the rim behind a mark is drawn. The glyph fills about seven tenths of its tile, so this shows as
 *  a couple of pixels of the other colour all the way round rather than as a second, fatter mark. */
const HALO_SCALE = 1.22;

/** How much bigger the chosen mark is drawn. Colour alone is not a selection on a map of four thousand marks at
 *  four pixels, and the colour is the community's accent, which a reader has been looking at all day; it also has
 *  to be the thing the eye lands on. */
const CHOSEN_SCALE = 1.35;

function pointsOf(
  position: THREE.BufferAttribute,
  colours: Float32Array,
  map: THREE.Texture,
  size: number,
): THREE.Points<THREE.BufferGeometry, THREE.PointsMaterial> {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", position);
  geometry.setAttribute("color", new THREE.BufferAttribute(colours, 3));
  // Not attenuated: a mark keeps one apparent size however far away it stands, which is what lets zooming
  // separate two marks instead of magnifying both of them equally.
  const material = new THREE.PointsMaterial({
    size,
    map,
    vertexColors: true,
    transparent: true,
    alphaTest: 0.35,
    sizeAttenuation: false,
  });
  return new THREE.Points(geometry, material);
}

export function markPoints(
  marks: PlacedContribution[],
  place: Place,
  look: Look,
): MarkLayer {
  const { atlas, detail, markPx, pixelRatio } = look;
  const palette = groundPalette();
  const { ink, halo: rim } = markInk(palette);
  const points: MarkLayer["points"] = [];
  const halos: MarkLayer["points"] = [];
  const tints: Tinted[] = [];
  const spent: { dispose: () => void }[] = [];
  // What each object's size is a multiple of, so a zoom resizes the chosen mark by the same factor as the rest
  // rather than flattening it back to theirs.
  const scales = new Map<Glyphs, number>();
  const shadesOf = (full: typeof ink): Shades => ({
    full: linearOf(full),
    back: linearOf(setBack(full, palette)),
  });
  const glyphShades = shadesOf(ink);
  const rimShades = shadesOf(rim);
  const colours = (count: number, shade: Linear) => {
    const out = new Float32Array(count * 3);
    for (let index = 0; index < count; index++) out.set(shade, index * 3);
    return out;
  };

  function pairOf({
    positions,
    inked,
    rimmed,
    map,
    scale,
  }: Readonly<{
    positions: number[];
    inked: Float32Array;
    rimmed: Float32Array;
    map: THREE.Texture;
    scale: number;
  }>): Pair {
    // One buffer for both passes: they are the same marks in the same places, drawn twice at two sizes.
    const position = new THREE.Float32BufferAttribute(positions, 3);
    const rimLayer = pointsOf(
      position,
      rimmed,
      map,
      markPx * scale * HALO_SCALE * pixelRatio,
    );
    const glyph = pointsOf(position, inked, map, markPx * scale * pixelRatio);
    scales.set(rimLayer, scale * HALO_SCALE);
    scales.set(glyph, scale);
    points.push(glyph);
    halos.push(rimLayer);
    spent.push(
      glyph.geometry,
      glyph.material,
      rimLayer.geometry,
      rimLayer.material,
      map,
    );
    return { glyph, rim: rimLayer };
  }

  const chosenMark =
    look.chosen === undefined
      ? null
      : (marks.find((mark) => mark.signalId === look.chosen) ?? null);
  const rest =
    chosenMark === null ? marks : marks.filter((mark) => mark !== chosenMark);

  for (const [tile, group] of byTile(rest, detail)) {
    const positions: number[] = [];
    for (const mark of group) {
      const point = place(mark.x, mark.y);
      positions.push(point.x, point.y, point.z);
    }

    const pair = pairOf({
      positions,
      inked: colours(group.length, glyphShades.full),
      rimmed: colours(group.length, rimShades.full),
      map: tileTexture(atlas, tile),
      scale: 1,
    });
    const xy = layoutXy(group);
    const colourOf = (object: Glyphs) =>
      object.geometry.getAttribute("color") as THREE.BufferAttribute;
    tints.push(
      markTint({
        marks: group,
        xy,
        colour: colourOf(pair.glyph),
        shades: glyphShades,
      }),
      markTint({
        marks: group,
        xy,
        colour: colourOf(pair.rim),
        shades: rimShades,
      }),
    );
    // All four sit at the same depth, so the order they are drawn in is stated rather than left to the sort.
    pair.rim.renderOrder = 0;
    pair.glyph.renderOrder = 1;
  }

  let chosen: Pair | null = null;
  if (chosenMark !== null) {
    const spot = place(chosenMark.x, chosenMark.y);
    const { ink: chosenGlyph, halo: chosenRim } = chosenInk(palette);
    chosen = pairOf({
      positions: [spot.x, spot.y, spot.z],
      inked: colours(1, linearOf(chosenGlyph)),
      rimmed: colours(1, linearOf(chosenRim)),
      map: tileTexture(atlas, tileFor(chosenMark.sourceType, detail)),
      scale: CHOSEN_SCALE,
    });
    chosen.rim.renderOrder = 2;
    chosen.glyph.renderOrder = 3;
  }

  return {
    points,
    halos,
    chosen,
    tints,
    resize: (px) => {
      for (const [object, scale] of scales) {
        object.material.size = px * scale * pixelRatio;
      }
    },
    dispose: () => {
      for (const item of spent) item.dispose();
    },
  };
}
