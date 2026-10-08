import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";

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

/**
 * The mark a reader chose. It used to be a ring drawn in the document over the mark, which a crowded area never
 * got: a target is only given where a whole undisturbed one fits, and a click on an area dense enough to want a
 * selection showed nothing at all.
 */
describe("TerrainCanvas and the chosen mark", () => {
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
    document.documentElement.style.removeProperty("--primary");
    vi.restoreAllMocks();
  });

  const meshes = (scene: THREE.Scene) =>
    scene.children.filter(
      (child): child is THREE.Mesh => child instanceof THREE.Mesh,
    );

  it("paints it in the map's chosen ink, and nothing else in it", () => {
    document.documentElement.style.setProperty("--primary", "#ff00aa");

    draw({ marks: [mark(1), mark(2)], chosen: 1 });
    const scene = frame();

    expect(layerAt(scene, 3, 0)).toBe("ff00aa");
    expect(colourAt(scene, 0)).not.toBe("ff00aa");
  });

  it("draws it over the marks around it, and bigger than them", () => {
    draw({ marks: [mark(1), mark(2)], chosen: 1 });
    const scene = frame();
    const sizeOf = (renderOrder: number) =>
      (
        scene.children.find(
          (child): child is THREE.Points =>
            child instanceof THREE.Points && child.renderOrder === renderOrder,
        )!.material as THREE.PointsMaterial
      ).size;

    expect(sizeOf(3)).toBeGreaterThan(sizeOf(1));
  });

  /**
   * A click must not rebuild the ground. The heightfield is tens of thousands of shaded vertices and the marks are
   * a buffer write, so they are built by two effects rather than one: same meshes, new glyphs.
   */
  it("keeps the ground it already built when the choice changes", () => {
    // The same surface and the same highlight, which is what the view hands it: only the choice moves.
    const props = propsFor({ marks: [mark(1), mark(2)], chosen: 1 });
    const { rerender } = render(<TerrainCanvas {...props} />);
    const before = meshes(frame());

    rerender(<TerrainCanvas {...props} chosen={2} />);

    expect(meshes(frame())).toStrictEqual(before);
    expect(layerAt(frame(), 3, 0)).toBe(
      new THREE.Color(groundPalette().chosen).getHexString(),
    );
  });
});
