import * as THREE from "three";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import {
  ATLAS_ORDER,
  ATLAS_TILE,
  buildAtlas,
  tileFor,
  tileRect,
} from "@/features/expertise/ui/mark-atlas";
import { markInk, setBack } from "@/features/expertise/ui/mark-ink";
import { detailFor, LOD_ICON_PX } from "@/features/expertise/ui/mark-lod";
import {
  MARK_LIFT,
  markPoints,
  type Place,
} from "@/features/expertise/ui/mark-sprites";
import { ZOOM_MAX, ZOOM_MIN } from "@/features/expertise/ui/terrain-aim";
import { type TerrainView } from "@/features/expertise/ui/terrain-camera";
import { markPxAt } from "@/features/expertise/ui/terrain-mark-size";
import { type Rgb } from "@/features/expertise/ui/terrain-shading";

describe("the glyph atlas", () => {
  it("holds an icon and a fallback shape for every kind", () => {
    expect(new Set(ATLAS_ORDER).size).toBe(8);
    expect(ATLAS_ORDER).toContain("mic-icon");
    expect(ATLAS_ORDER).toContain("mic-shape");
  });

  it("gives each tile its own slot, side by side", () => {
    expect(tileRect(ATLAS_ORDER[0]!)).toEqual({
      x: 0,
      y: 0,
      w: ATLAS_TILE,
      h: ATLAS_TILE,
    });
    expect(tileRect(ATLAS_ORDER[1]!).x).toBe(ATLAS_TILE);
  });

  it("maps a tile to normalised texture coordinates inside the atlas", () => {
    expect(
      tileRect(ATLAS_ORDER[1]!).x / (ATLAS_TILE * ATLAS_ORDER.length),
    ).toBeCloseTo(1 / ATLAS_ORDER.length, 6);
  });
});

describe("tileFor", () => {
  it("picks the real icon once it is legible", () => {
    expect(tileFor("SLOT", detailFor(LOD_ICON_PX))).toBe("mic-icon");
    expect(tileFor("TOPIC", detailFor(20))).toBe("question-icon");
  });

  it("falls back to the shape below that", () => {
    expect(tileFor("SLOT", detailFor(4))).toBe("mic-shape");
    expect(tileFor("ACTION_ITEM", detailFor(4))).toBe("check-shape");
  });

  it("keeps the four fallback shapes distinguishable from each other", () => {
    const shapes = (["SLOT", "ARTIFACT", "TOPIC", "ACTION_ITEM"] as const).map(
      (t) => tileFor(t, "shape"),
    );

    expect(new Set(shapes).size).toBe(4);
  });
});

/** jsdom has no 2D context, so the painters are exercised against a recorder rather than against pixels. */
function recordingContext() {
  const translations: number[] = [];
  return {
    translations,
    ctx: {
      lineCap: "",
      lineJoin: "",
      lineWidth: 0,
      font: "",
      textAlign: "",
      textBaseline: "",
      fillStyle: "",
      strokeStyle: "",
      globalCompositeOperation: "",
      save: () => {},
      restore: () => {},
      translate: (x: number) => translations.push(x),
      beginPath: () => {},
      closePath: () => {},
      moveTo: () => {},
      lineTo: () => {},
      arc: () => {},
      roundRect: () => {},
      fill: () => {},
      stroke: () => {},
      fillRect: () => {},
      fillText: () => {},
    } as unknown as CanvasRenderingContext2D,
  };
}

describe("buildAtlas", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sizes one row of tiles and paints every one of them", () => {
    const recorder = recordingContext();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      recorder.ctx,
    );

    const canvas = buildAtlas();

    expect(canvas.width).toBe(ATLAS_TILE * ATLAS_ORDER.length);
    expect(canvas.height).toBe(ATLAS_TILE);
    expect(recorder.translations).toEqual(
      ATLAS_ORDER.map((tile) => tileRect(tile).x),
    );
  });

  it("returns a blank canvas rather than throwing where there is no 2D context", () => {
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);

    expect(buildAtlas().width).toBe(ATLAS_TILE * ATLAS_ORDER.length);
  });
});

/**
 * `PointsMaterial.size` is in world units under `sizeAttenuation`, and the atlas tile is chosen by apparent pixels.
 * They were once the same number, which made a mark grow as the camera approached instead of gaining its icon.
 */
describe("markPoints", () => {
  const view = (zoom: number): TerrainView => ({
    zoom,
    size: { width: 800, height: 500 },
    projection: "flat",
  });

  const marks: PlacedContribution[] = [
    {
      signalId: 1,
      tag: "kafka",
      x: 10,
      y: 20,
      slot: 0,
      sourceType: "SLOT",
      userId: "u1",
      occurredAt: 0,
      reach: null,
    },
  ];

  const place: Place = (x, y) => new THREE.Vector3(x, 0, y);

  function layerAt(zoom: number, pixelRatio = 1) {
    const atlas = new THREE.CanvasTexture(document.createElement("canvas"));
    const markPx = markPxAt(view(zoom));
    const layer = markPoints(marks, place, {
      atlas,
      detail: detailFor(markPx),
      markPx,
      pixelRatio,
    });
    const material = layer.points[0]!.material;
    return {
      size: material.size,
      attenuated: material.sizeAttenuation,
      offset: material.map!.offset.x,
    };
  }

  /** A `PointsMaterial` has one opacity for the whole layer, so a mark a reading is not about gives up its colour
   *  rather than its presence. */
  function colourOf(lit?: (mark: PlacedContribution) => boolean) {
    const atlas = new THREE.CanvasTexture(document.createElement("canvas"));
    const layer = markPoints(marks, place, {
      atlas,
      detail: "icon",
      markPx: 10,
      pixelRatio: 1,
      ...(lit === undefined ? {} : { lit }),
    });
    const colour = layer.points[0]!.geometry.getAttribute("color");
    return [colour.getX(0), colour.getY(0), colour.getZ(0)];
  }

  /** The attribute holds float32, so the comparison is per channel rather than on the triple. */
  function expectColour(actual: number[], wanted: Rgb): void {
    const colour = new THREE.Color().setRGB(
      wanted.r,
      wanted.g,
      wanted.b,
      THREE.SRGBColorSpace,
    );
    expect(actual[0]).toBeCloseTo(colour.r, 6);
    expect(actual[1]).toBeCloseTo(colour.g, 6);
    expect(actual[2]).toBeCloseTo(colour.b, 6);
  }

  it("draws every mark the same when the panel is about the whole map", () => {
    expect(colourOf()).toStrictEqual(colourOf(() => true));
  });

  /**
   * A mark used to carry its own area's accent, which is the colour the ground under it is painted in: measured
   * against that ground it came back at about 1.2 to 1. The ground alone says which area a mark belongs to.
   */
  it("draws a mark in ink picked for contrast against the ground under it", () => {
    expectColour(colourOf(), markInk(groundPalette()).ink);
  });

  /** Picked against the ground, the pair inverted between two areas the reader cannot tell apart. */
  it("draws every mark the same, whatever area it belongs to", () => {
    const atlas = new THREE.CanvasTexture(document.createElement("canvas"));
    const layer = markPoints(
      marks.map((mark, index) => ({
        ...mark,
        signalId: index + 1,
        tag: `area-${index}`,
      })),
      place,
      { atlas, detail: "icon", markPx: 10, pixelRatio: 1 },
    );
    const colour = layer.points[0]!.geometry.getAttribute("color");

    for (let index = 1; index < colour.count; index++) {
      expect(colour.getX(index)).toBeCloseTo(colour.getX(0), 6);
    }
  });

  it("sets back a mark the reading is not about, without erasing it", () => {
    const palette = groundPalette();

    expectColour(
      colourOf(() => false),
      setBack(markInk(palette).ink, palette),
    );
    expect(colourOf(() => false)).not.toStrictEqual(colourOf(() => true));
  });

  /**
   * `gl_PointSize` counts device pixels, so a point given its size in CSS pixels draws at half of it on a retina
   * screen - and a level of detail stated in pixels silently stops meaning pixels.
   */
  it("sizes a mark in device pixels, so it draws the size it was asked for", () => {
    expect(layerAt(1, 1).size).toBe(markPxAt(view(1)));
    expect(layerAt(1, 2).size).toBe(markPxAt(view(1)) * 2);
  });

  /** Attenuated, a mark far from the camera would draw smaller than the near one beside it and mean the same thing. */
  it("does not let the distance to a mark change how big it draws", () => {
    expect(layerAt(1).attenuated).toBe(false);
  });

  it("changes the glyph it draws as the apparent size crosses the threshold", () => {
    expect(layerAt(ZOOM_MIN).offset).not.toBe(layerAt(ZOOM_MAX).offset);
  });

  it("lifts a mark clear of the ground it stands on", () => {
    expect(MARK_LIFT).toBeGreaterThan(0);
  });
});
