import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { chosenInk } from "@/features/expertise/ui/mark-ink";
import { markPoints, type Place } from "@/features/expertise/ui/mark-sprites";

/**
 * The mark a reader chose. A ring drawn around it in the document said the same thing twice the size, and only for
 * the marks whose ground was sparse enough to leave them a target: on a crowded area a chosen mark showed nothing
 * at all. Here it is the mark itself that changes, so it says it wherever it stands.
 */
describe("the chosen mark", () => {
  const place: Place = (x, y) => new THREE.Vector3(x, 0, y);

  const mark = (signalId: number): PlacedContribution => ({
    signalId,
    tag: "kafka",
    x: signalId,
    y: 0,
    slot: 0,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: 0,
    reach: null,
  });

  function layer(chosen?: number) {
    const atlas = new THREE.CanvasTexture(document.createElement("canvas"));
    return markPoints([mark(1), mark(2)], place, {
      atlas,
      detail: "icon",
      markPx: 10,
      pixelRatio: 1,
      ...(chosen === undefined ? {} : { chosen }),
    });
  }

  it("draws no chosen mark while nothing is chosen", () => {
    expect(layer().chosen).toBeNull();
  });

  /** Drawn on top of itself instead, an outline glyph scaled up frames the smaller one rather than covering it,
   *  and the chosen mark reads as a pink ring around a white one. */
  it("takes the chosen mark out of the layer the rest of them are drawn in", () => {
    expect(layer(1).points[0]!.geometry.getAttribute("position").count).toBe(1);
  });

  it("paints it in the chosen ink", () => {
    const colour = layer(1).chosen!.glyph.geometry.getAttribute("color");
    const wanted = new THREE.Color().setRGB(
      chosenInk(groundPalette()).ink.r,
      chosenInk(groundPalette()).ink.g,
      chosenInk(groundPalette()).ink.b,
      THREE.SRGBColorSpace,
    );

    expect(colour.getX(0)).toBeCloseTo(wanted.r, 6);
    expect(colour.getY(0)).toBeCloseTo(wanted.g, 6);
    expect(colour.getZ(0)).toBeCloseTo(wanted.b, 6);
  });

  it("draws it bigger than the marks around it, and over them", () => {
    const built = layer(1);

    expect(built.chosen!.glyph.material.size).toBeGreaterThan(
      built.points[0]!.material.size,
    );
    expect(built.chosen!.glyph.renderOrder).toBeGreaterThan(
      built.points[0]!.renderOrder,
    );
  });

  it("keeps it bigger than the rest as the zoom resizes the layer", () => {
    const built = layer(1);
    built.resize(24);

    expect(built.chosen!.glyph.material.size).toBeGreaterThan(
      built.points[0]!.material.size,
    );
  });

  /** A selection outlives the payload it was made against: a reload, or a new page of contributions, can leave an
   *  id behind that no mark answers to. */
  it("draws every mark as usual when the chosen one is not on the map", () => {
    expect(layer(99).chosen).toBeNull();
    expect(layer(99).points[0]!.geometry.getAttribute("position").count).toBe(
      2,
    );
  });

  /** Both canvases add `points` and `halos` to the scene and know nothing else, so a chosen mark left out of them
   *  is built, disposed, and never drawn. */
  it("is among the objects a renderer draws", () => {
    const built = layer(1);

    expect(built.points).toContain(built.chosen!.glyph);
    expect(built.halos).toContain(built.chosen!.rim);
  });
});
