import { act, render, screen } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { MapPerson } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import { ATLAS_ORDER } from "@/features/expertise/ui/mark-atlas";
import type { Spin } from "@/features/expertise/ui/terrain-aim";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
import * as webgl from "@/features/expertise/ui/webgl-support";

const gpu = vi.hoisted(() => ({
  stencil: false,
  disposed: 0,
  released: 0,
  drawn: 0,
  frame: null as (() => void) | null,
  scene: null as THREE.Scene | null,
  camera: null as THREE.PerspectiveCamera | null,
}));

// Only the renderer is faked. jsdom has no GL context, but every other three.js object works here, so what the
// component actually built can be read back off the scene instead of counted through a stub.
vi.mock("three", async (importOriginal) => ({
  ...(await importOriginal<typeof import("three")>()),
  WebGLRenderer: class {
    constructor(options: { stencil?: boolean }) {
      gpu.stencil = options.stencil === true;
    }
    domElement = document.createElement("canvas");
    setSize() {}
    setPixelRatio() {}
    setClearColor() {}
    setAnimationLoop(frame: (() => void) | null) {
      gpu.frame = frame;
    }
    render(scene: THREE.Scene, camera: THREE.PerspectiveCamera) {
      gpu.drawn += 1;
      gpu.scene = scene;
      gpu.camera = camera;
    }
    forceContextLoss() {
      gpu.released += 1;
    }
    dispose() {
      gpu.disposed += 1;
    }
  },
}));

type Props = ComponentProps<typeof TerrainCanvas>;

const size = { width: 800, height: 500 };

const people = new Map<string, MapPerson>([
  ["u-1", { userId: "u-1", displayName: "Ada Lovelace", avatarUrl: null }],
]);

const offered: PlacedTopic = {
  tag: "kafka",
  x: 200,
  y: 150,
  r: 20,
  declarations: 3,
  practised: true,
};

function mark(
  signalId: number,
  sourceType: PlacedContribution["sourceType"] = "SLOT",
): PlacedContribution {
  return {
    signalId,
    sourceType,
    tag: "kafka",
    x: 120,
    y: 90,
    slot: 0,
    userId: "u-1",
    occurredAt: 0,
    reach: 40,
  };
}

type Options = Partial<
  Omit<Props, "surface" | "view" | "chosen"> & {
    projection: Projection;
    zoom: number;
    spin: Spin;
  }
>;
function propsFor(options: Options = {}): Props {
  const marks = options.marks ?? [];
  const projection = options.projection ?? "flat";
  const view: Props["view"] = { zoom: options.zoom ?? 1, size, projection };
  return {
    surface: { field: buildHeightfield(marks, size), size, projection },
    view: options.spin === undefined ? view : { ...view, spin: options.spin },
    marks,
    topics: options.topics ?? [],
    people: options.people ?? new Map(),
    faced: options.faced ?? new Set(),
    highlight: options.highlight ?? { tags: null, marks: null },
    // Nothing here is about the chosen mark; `TerrainCanvas.chosen.test.tsx` is.
    chosen: null,
  };
}

const draw = (options: Options = {}) =>
  render(<TerrainCanvas {...propsFor(options)} />);

/** Drives the frame a change asked for, if any, and reads back the scene the fake renderer was last handed. */
function frame(): THREE.Scene {
  act(() => gpu.frame?.());
  return gpu.scene!;
}

function pointsOf(scene: THREE.Scene): THREE.Points[] {
  return scene.children.filter(
    (c): c is THREE.Points => c instanceof THREE.Points,
  );
}

function spritesOf(scene: THREE.Scene) {
  return scene.children.filter((c) => c instanceof THREE.Sprite);
}

/** The glyph pass, not the rim drawn behind it: both are `Points` at the same places, and the rim is drawn first. */
function glyphsOf(scene: THREE.Scene): THREE.Points[] {
  return pointsOf(scene).filter((points) => points.renderOrder === 1);
}

function tileOffsets(scene: THREE.Scene): number[] {
  return glyphsOf(scene).map(
    (points) => (points.material as THREE.PointsMaterial).map!.offset.x,
  );
}

function attributeOf(scene: THREE.Scene, name: string) {
  return glyphsOf(scene)[0]!.geometry.getAttribute(name);
}

describe("TerrainCanvas", () => {
  beforeEach(() => {
    Object.assign(gpu, {
      disposed: 0,
      released: 0,
      drawn: 0,
      frame: null,
      scene: null,
    });
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("says so rather than rendering nothing when WebGL2 is missing", () => {
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(false);

    draw();

    expect(screen.getByRole("status")).toHaveTextContent(
      /map needs a browser with WebGL/i,
    );
  });

  it("hides the drawing from assistive technology", () => {
    const { container } = draw();

    expect(container.querySelector("[aria-hidden='true']")).not.toBeNull();
  });

  /** `dispose` frees what three.js allocated and leaves the context to the collector. A browser keeps about
   *  sixteen contexts alive, so one held until a collection nobody controls is one the next drawing may not get. */
  it("hands the GPU context back on unmount rather than waiting to be collected", () => {
    draw().unmount();

    expect(gpu.disposed).toBe(1);
    expect(gpu.released).toBe(1);
  });

  it("draws one points object per kind, each on its own tile of the atlas", () => {
    draw({ marks: [mark(1), mark(2), mark(3, "ARTIFACT")] });

    expect(tileOffsets(frame())).toEqual([
      ATLAS_ORDER.indexOf("mic-icon") / ATLAS_ORDER.length,
      ATLAS_ORDER.indexOf("doc-icon") / ATLAS_ORDER.length,
    ]);
  });

  it("lifts the same layout onto a sphere rather than laying it flat", () => {
    draw({ marks: [mark(1)], projection: "globe" });

    const at = new THREE.Vector3().fromBufferAttribute(
      attributeOf(frame(), "position"),
      0,
    );

    expect(at.y).toBeGreaterThan(50);
  });

  it("draws no face when nothing cleared the reach gate", () => {
    draw({ marks: [mark(1)], faced: new Set(), people });

    expect(spritesOf(frame())).toHaveLength(0);
  });

  it("draws one face per contribution that cleared it", () => {
    draw({ marks: [mark(1)], faced: new Set([1]), people });

    expect(spritesOf(frame())).toHaveLength(1);
  });

  it("rings an offered area with a dash rather than a solid line", () => {
    draw({ topics: [offered] });

    const [loop] = frame().children.filter(
      (c): c is THREE.LineLoop => c instanceof THREE.LineLoop,
    );

    expect(loop!.material).toBeInstanceOf(THREE.LineDashedMaterial);
    expect(loop!.geometry.getAttribute("lineDistance")).toBeDefined();
  });

  it("draws what stands on the globe only over its ground, so nothing hangs past the limb", () => {
    draw({
      marks: [mark(1)],
      faced: new Set([1]),
      people,
      topics: [offered],
      projection: "globe",
    });
    const scene = frame();
    const [ground, standing] = [true, false].map((isGround) =>
      scene.children
        .filter((c) => "material" in c && c instanceof THREE.Mesh === isGround)
        .map((c) => (c as THREE.Mesh).material as THREE.Material),
    );

    expect(gpu.stencil).toBe(true);
    expect(ground!.map((m) => m.stencilZPass)).toStrictEqual(
      ground!.map(() => THREE.ReplaceStencilOp),
    );
    expect(standing!.map((m) => m.stencilFunc)).toStrictEqual(
      standing!.map(() => THREE.EqualStencilFunc),
    );
    // The cap and the heightfield; a mark's rim and glyph, its face and the offered ring.
    expect([ground!.length, standing!.length]).toStrictEqual([2, 4]);
  });

  it("draws only while the map is on screen", () => {
    type Notify = (entries: { isIntersecting: boolean }[]) => void;
    const observer: { notify: Notify } = { notify: () => {} };
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        constructor(notify: Notify) {
          observer.notify = notify;
        }
        observe() {}
        disconnect() {}
      },
    );
    const { rerender } = draw();

    act(() => observer.notify([{ isIntersecting: true }]));
    frame();
    act(() => observer.notify([]));
    rerender(<TerrainCanvas {...propsFor({ zoom: 2 })} />);
    frame();
    expect(gpu.drawn).toBe(1);

    act(() => observer.notify([{ isIntersecting: true }]));
    frame();
    expect(gpu.drawn).toBe(2);
  });
});
