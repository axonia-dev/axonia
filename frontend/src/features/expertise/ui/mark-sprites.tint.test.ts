import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { paintNow } from "@/features/expertise/ui/echo-blend";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import type { MapHighlight } from "@/features/expertise/ui/map-highlight";
import { markInk, setBack } from "@/features/expertise/ui/mark-ink";
import {
  type MarkLayer,
  markPoints,
} from "@/features/expertise/ui/mark-sprites";
import type { Rgb } from "@/features/expertise/ui/terrain-shading";

const mark = (
  signalId: number,
  tag: string,
  x: number,
): PlacedContribution => ({
  signalId,
  tag,
  x,
  y: 40,
  slot: 0,
  sourceType: "SLOT",
  userId: "u1",
  occurredAt: 0,
  reach: null,
});

const marks = [mark(1, "kafka", 10), mark(2, "rust", 30)];
const onMarkOne: MapHighlight = { tags: null, marks: new Set([1]) };

function layerOf(chosen?: number) {
  return markPoints(marks, (x, y) => new THREE.Vector3(x, 0, y), {
    atlas: new THREE.CanvasTexture(document.createElement("canvas")),
    detail: "icon",
    markPx: 10,
    pixelRatio: 1,
    ...(chosen === undefined ? {} : { chosen }),
  });
}

function tintOf(layer: MarkLayer, object: MarkLayer["points"][number]) {
  const colours = object.geometry.getAttribute("color").array;
  return layer.tints.find((tint) => tint.values === colours)!;
}

function linear(...colours: Rgb[]): Float32Array {
  const out = new Float32Array(colours.length * 3);
  const scratch = new THREE.Color();
  colours.forEach(({ r, g, b }, index) => {
    scratch.setRGB(r, g, b, THREE.SRGBColorSpace);
    out.set([scratch.r, scratch.g, scratch.b], index * 3);
  });
  return out;
}

describe("the marks' tints", () => {
  const palette = groundPalette();
  const { ink, halo } = markInk(palette);

  it("sets back the glyph of a mark the highlight is not about", () => {
    const layer = layerOf();
    expect(tintOf(layer, layer.points[0]!).targetFor(onMarkOne)).toStrictEqual(
      linear(ink, setBack(ink, palette)),
    );
  });

  it("sets back its rim the same way", () => {
    const layer = layerOf();
    expect(tintOf(layer, layer.halos[0]!).targetFor(onMarkOne)).toStrictEqual(
      linear(halo, setBack(halo, palette)),
    );
  });

  it("places each item at its mark's layout position", () => {
    const [tint] = layerOf().tints;

    expect(tint!.stride).toBe(3);
    expect([...tint!.xy]).toStrictEqual([10, 40, 30, 40]);
  });

  it("writes a painted highlight into the object it tints", () => {
    const layer = layerOf();
    const glyph = tintOf(layer, layer.points[0]!);
    const colour = layer.points[0]!.geometry.getAttribute(
      "color",
    ) as THREE.BufferAttribute;
    const before = colour.version;

    paintNow(glyph, onMarkOne);

    expect(colour.array).toStrictEqual(glyph.targetFor(onMarkOne));
    expect(colour.version).toBeGreaterThan(before);
  });

  it("leaves the chosen pair in the chosen ink, untinted", () => {
    const layer = layerOf(2);
    const tinted = new Set(layer.tints.map((tint) => tint.values));

    expect(layer.tints).toHaveLength(2);
    expect(
      tinted.has(
        layer.chosen!.glyph.geometry.getAttribute("color")
          .array as Float32Array,
      ),
    ).toBe(false);
  });
});
