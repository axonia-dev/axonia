import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { DEEPEST } from "@/features/expertise/ui/terrain-ground";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { groundColourAt } from "@/features/expertise/ui/terrain-shading";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";
import { switchTheme } from "@/test/theme";

const gpu = vi.hoisted(() => ({
  disposed: 0,
  frame: null as (() => void) | null,
  cleared: null as { hex: string; alpha: number } | null,
  scene: null as THREE.Scene | null,
}));

// Only the renderer is faked. jsdom has no GL context, but every other three.js object works here, so what the
// component actually built can be read back off the scene instead of counted through a stub.
vi.mock("three", async (importOriginal) => ({
  ...(await importOriginal<typeof import("three")>()),
  WebGLRenderer: class {
    domElement = document.createElement("canvas");
    setSize() {}
    setPixelRatio() {}
    setClearColor(colour: THREE.Color, alpha: number) {
      gpu.cleared = { hex: colour.getHexString(), alpha };
    }
    setAnimationLoop(frame: (() => void) | null) {
      gpu.frame = frame;
    }
    render(scene: THREE.Scene) {
      gpu.scene = scene;
    }
    forceContextLoss() {}
    dispose() {
      gpu.disposed += 1;
    }
  },
}));

type Props = ComponentProps<typeof TerrainCanvas>;

const size = { width: 800, height: 500 };

function mark(signalId: number): PlacedContribution {
  return {
    signalId,
    sourceType: "SLOT",
    tag: "kafka",
    x: 120,
    y: 90,
    slot: 0,
    userId: "u-1",
    occurredAt: 0,
    reach: 40,
  };
}

function propsFor(options: {
  marks?: PlacedContribution[];
  projection?: Projection;
  chosen?: number | null;
}): Props {
  const marks = options.marks ?? [];
  const projection = options.projection ?? "flat";
  return {
    surface: { field: buildHeightfield(marks, size), size, projection },
    view: { zoom: 1, size, projection },
    marks,
    topics: [],
    people: new Map(),
    faced: new Set(),
    highlight: { tags: null, marks: null },
    chosen: options.chosen ?? null,
  };
}

function draw(
  options: {
    marks?: PlacedContribution[];
    projection?: Projection;
    chosen?: number | null;
  } = {},
) {
  return render(<TerrainCanvas {...propsFor(options)} />);
}

/** Drives the frame a change asked for, if any, and reads back the scene the fake renderer was last handed. */
function frame(): THREE.Scene {
  act(() => gpu.frame?.());
  return gpu.scene!;
}

/** A mark is drawn twice, the rim first and the glyph over it, so which of the two is asked matters here. */
function layerAt(
  scene: THREE.Scene,
  renderOrder: number,
  index: number,
): string {
  const [points] = scene.children.filter(
    (c): c is THREE.Points =>
      c instanceof THREE.Points && c.renderOrder === renderOrder,
  );
  return new THREE.Color()
    .fromBufferAttribute(points!.geometry.getAttribute("color"), index)
    .getHexString();
}

const colourAt = (scene: THREE.Scene, index: number) =>
  layerAt(scene, 1, index);
const haloAt = (scene: THREE.Scene, index: number) => layerAt(scene, 0, index);

/** The glyph and the rim behind it: one pair for the whole map, which way round being the theme's. */
function inks(glyph: string, rim: string): void {
  document.documentElement.style.setProperty("--map-glyph", glyph);
  document.documentElement.style.setProperty("--map-glyph-rim", rim);
}

describe("TerrainCanvas paint", () => {
  beforeEach(() => {
    Object.assign(gpu, {
      disposed: 0,
      frame: null,
      scene: null,
      cleared: null,
    });
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    for (const token of [
      "--ring-teal-a",
      "--map-glyph",
      "--map-glyph-rim",
      "--map-chosen",
    ]) {
      document.documentElement.style.removeProperty(token);
    }
    document.documentElement.className = "";
    vi.restoreAllMocks();
  });

  /**
   * A mark used to carry its own area's accent, which is the colour the ground under it is painted in, so it was
   * drawn the colour of the thing it stands on and measured about 1.2 to 1 against it. The ground alone says which
   * area a mark belongs to; the mark is ink over it.
   */
  it("draws a mark in ink rather than in the colour of the ground under it", () => {
    document.documentElement.style.setProperty("--ring-teal-a", "#0f8f8f");

    draw({ marks: [mark(1)] });

    expect(colourAt(frame(), 0)).not.toBe("0f8f8f");
  });

  /**
   * WebGL reads no cascade: every colour is resolved out of it once, when the layer is built. Without a watch on
   * the theme a reader who switched it kept the old map until the page was reloaded.
   */
  it("repaints when the theme is switched under it", async () => {
    inks("#101010", "#f0f0f0");
    draw({ marks: [mark(1)] });
    const before = colourAt(frame(), 0);

    inks("#202020", "#e0e0e0");
    await switchTheme("theme-dark");

    expect(colourAt(frame(), 0)).not.toBe(before);
  });

  /**
   * One ink cannot serve an area that runs from its coast to its summit, and on the globe the marks at the limb
   * were black on near-black ground. A mark carries a rim in the other colour, the way every name on this drawing
   * already does, so what it is legible against is itself rather than the ground.
   */
  it("draws a rim behind each mark, in the ink the glyph is not", () => {
    inks("#101010", "#f0f0f0");

    draw({ marks: [mark(1)] });
    const scene = frame();

    expect(new Set([colourAt(scene, 0), haloAt(scene, 0)])).toStrictEqual(
      new Set(["101010", "f0f0f0"]),
    );
  });

  it("draws the rim wider than the glyph it sits behind", () => {
    draw({ marks: [mark(1)] });
    const sizeOf = (renderOrder: number) =>
      (
        frame().children.find(
          (child): child is THREE.Points =>
            child instanceof THREE.Points && child.renderOrder === renderOrder,
        )!.material as THREE.PointsMaterial
      ).size;

    expect(sizeOf(0)).toBeGreaterThan(sizeOf(1));
  });

  /** A context is scarce and the theme is not a reason to spend one: only what is drawn is rebuilt. */
  it("repaints without taking another GPU context", async () => {
    draw({ marks: [mark(1)] });
    const spent = gpu.disposed;

    await switchTheme("theme-dark");

    expect(gpu.disposed).toBe(spent);
  });

  /**
   * Light intensities are in the renderer's own physical units, and have been since three r155 - a light is no
   * longer a plain multiplier on the albedo and is short by a factor of PI. Read as multipliers, the globe drew at
   * under half the colour the ramp asked for. Summing to PI is what makes the brightest face the ramp's own colour.
   */
  it("lights the scene to the full colour the ramp asks for, and no further", () => {
    draw({ projection: "globe" });
    const lights = frame().children.filter(
      (child): child is THREE.Light => child instanceof THREE.Light,
    );

    expect(lights.reduce((sum, light) => sum + light.intensity, 0)).toBeCloseTo(
      Math.PI,
      6,
    );
  });

  /** Fitted to the land rather than to the whole plane, the plane's far edge otherwise shows as a hard line with
   *  the page behind it. A map has no edge to see past. */
  it("clears the flat reading to its own deepest water", () => {
    draw({ projection: "flat" });
    const deep = groundColourAt(DEEPEST, groundPalette());

    expect(gpu.cleared).toStrictEqual({
      hex: new THREE.Color()
        .setRGB(deep.r, deep.g, deep.b, THREE.SRGBColorSpace)
        .getHexString(),
      alpha: 1,
    });
  });

  // The globe is a planet, and what is not the planet is not water.
  it("clears the globe to nothing, so the page shows behind it", () => {
    draw({ projection: "globe" });

    expect(gpu.cleared!.alpha).toBe(0);
  });
});
