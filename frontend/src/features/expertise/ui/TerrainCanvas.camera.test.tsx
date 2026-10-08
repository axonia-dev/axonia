import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import { ATLAS_ORDER } from "@/features/expertise/ui/mark-atlas";
import {
  type Pan,
  type Spin,
  ZOOM_MAX,
  ZOOM_MIN,
} from "@/features/expertise/ui/terrain-aim";
import {
  cameraEye,
  FAR,
  fovOf,
  NEAR,
} from "@/features/expertise/ui/terrain-camera";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { markPxAt } from "@/features/expertise/ui/terrain-mark-size";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";

const gpu = vi.hoisted(() => ({
  disposed: 0,
  drawn: 0,
  frame: null as (() => void) | null,
  cleared: null as { hex: string; alpha: number } | null,
  scene: null as THREE.Scene | null,
  camera: null as THREE.PerspectiveCamera | null,
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
    render(scene: THREE.Scene, camera: THREE.PerspectiveCamera) {
      gpu.drawn += 1;
      gpu.scene = scene;
      gpu.camera = camera;
    }
    forceContextLoss() {}
    dispose() {
      gpu.disposed += 1;
    }
  },
}));

type Props = ComponentProps<typeof TerrainCanvas>;

const size = { width: 800, height: 500 };

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
    pan: Pan;
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

/** The glyph pass, not the rim drawn behind it: both are `Points` at the same places, and the rim is drawn first. */
function glyphsOf(scene: THREE.Scene): THREE.Points[] {
  return pointsOf(scene).filter((points) => points.renderOrder === 1);
}

function tileOffsets(scene: THREE.Scene): number[] {
  return glyphsOf(scene).map(
    (points) => (points.material as THREE.PointsMaterial).map!.offset.x,
  );
}

function markSizeOf(scene: THREE.Scene): number {
  return (glyphsOf(scene)[0]!.material as THREE.PointsMaterial).size;
}

describe("the camera TerrainCanvas draws through", () => {
  beforeEach(() => {
    Object.assign(gpu, { disposed: 0, drawn: 0, frame: null, scene: null });
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => vi.restoreAllMocks());

  // The camera is not the renderer's own: the overlay projects its buttons through the same exports, so a mark and
  // the button that reaches it can never disagree about where it is.
  it("stands where the shared projection says it stands", () => {
    const props = propsFor({ zoom: 1.4 });
    render(<TerrainCanvas {...props} />);
    frame();

    const eye = cameraEye(props.view);
    expect(gpu.camera!.position.toArray()).toEqual([eye.x, eye.y, eye.z]);
    expect(gpu.camera!.fov).toBe(fovOf(props.view));
    expect(gpu.camera!.near).toBe(NEAR);
    expect(gpu.camera!.far).toBe(FAR);
    expect(gpu.camera!.aspect).toBe(size.width / size.height);
  });

  it("narrows the lens when the zoom prop grows, rather than holding its own", () => {
    const { rerender } = render(<TerrainCanvas {...propsFor()} />);
    frame();
    const before = gpu.camera!.fov;

    rerender(<TerrainCanvas {...propsFor({ zoom: ZOOM_MAX })} />);
    frame();

    expect(gpu.camera!.fov).toBeLessThan(before);
  });

  /**
   * The whole reason turning the globe is affordable. A drag changes the camera on every pointer event, and this
   * scene is a heightfield of tens of thousands of shaded vertices, an atlas and a sprite per face: rebuilding it
   * at that rate is not a drag, it is a slideshow. So the camera moves in place, and nothing else is touched.
   */
  it("moves the eye on a turn without rebuilding the scene under it", () => {
    const marks = [mark(1)];
    // The surface is the same object across both renders, as it is in the view that mounts this: it is memoised
    // apart from the camera there for exactly this reason.
    const props = propsFor({ marks, projection: "globe" });
    const { rerender } = render(<TerrainCanvas {...props} />);
    const built = frame();
    const before = gpu.camera!.position.clone();

    rerender(
      <TerrainCanvas
        {...props}
        view={{ ...props.view, spin: { yaw: 70, pitch: 25 } }}
      />,
    );
    frame();

    expect(gpu.camera!.position.equals(before)).toBe(false);
    expect(gpu.camera!.position.length()).toBeCloseTo(before.length(), 6);
    expect(gpu.scene).toBe(built);
    expect(gpu.disposed).toBe(0);
  });

  /**
   * A zoom is a dozen clicks of the wheel from one end of the range to the other, and the marks are sized in
   * pixels, so a layer keyed on that size would be rebuilt at every one of them. The size is set on the material
   * instead, and only the glyph set - which changes once across the whole range - can rebuild anything.
   */
  it("resizes the marks on a zoom without rebuilding the scene under them", () => {
    const props = propsFor({ marks: [mark(1)] });
    const { rerender } = render(<TerrainCanvas {...props} />);
    const built = frame();
    const before = markSizeOf(built);

    const closer = { ...props.view, zoom: 1.6 };
    rerender(<TerrainCanvas {...props} view={closer} />);
    frame();

    expect(markSizeOf(gpu.scene!)).toBeGreaterThan(before);
    expect(markSizeOf(gpu.scene!)).toBe(markPxAt(closer));
    expect(gpu.scene).toBe(built);
    expect(gpu.disposed).toBe(0);
  });

  it("delivers the real icon zoomed in and the fallback shape zoomed out", () => {
    const marks = [mark(1)];
    const { rerender } = render(
      <TerrainCanvas {...propsFor({ marks, zoom: ZOOM_MAX })} />,
    );

    expect(tileOffsets(frame())).toEqual([
      ATLAS_ORDER.indexOf("mic-icon") / ATLAS_ORDER.length,
    ]);

    rerender(<TerrainCanvas {...propsFor({ marks, zoom: ZOOM_MIN })} />);

    expect(tileOffsets(frame())).toEqual([
      ATLAS_ORDER.indexOf("mic-shape") / ATLAS_ORDER.length,
    ]);
  });

  /**
   * A face is sized by the camera, so a layer rebuilt for any other reason must come up at the size the camera last
   * set rather than at three.js's default scale of one, which, unattenuated, is a face as big as the globe.
   */
  it("sizes the faces of a rebuilt layer as the camera last sized them", () => {
    const props = propsFor({
      marks: [mark(1)],
      faced: new Set([1]),
      people: new Map([
        [
          "u-1",
          { userId: "u-1", displayName: "Ada Lovelace", avatarUrl: null },
        ],
      ]),
      projection: "globe",
      zoom: 2,
    });
    const faceOf = (scene: THREE.Scene) =>
      scene.children.find((child) => child instanceof THREE.Sprite)!;
    const { rerender } = render(<TerrainCanvas {...props} />);
    const sized = faceOf(frame()).scale.x;

    rerender(
      <TerrainCanvas
        {...props}
        highlight={{ tags: new Set(["kafka"]), marks: null }}
      />,
    );

    expect(sized).toBeLessThan(1);
    expect(faceOf(frame()).scale.x).toBe(sized);
  });
});
