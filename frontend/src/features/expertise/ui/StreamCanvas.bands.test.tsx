import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { ATLAS_ORDER, tileFor } from "@/features/expertise/ui/mark-atlas";
import { detailFor } from "@/features/expertise/ui/mark-lod";
import { PAD_X, RESTING_CAMERA } from "@/features/expertise/ui/stream-layout";
import { StreamCanvas } from "@/features/expertise/ui/StreamCanvas";
import { markPxFor } from "@/features/expertise/ui/terrain-mark-size";
import * as webgl from "@/features/expertise/ui/webgl-support";
import { switchTheme } from "@/test/theme";

const gpu = vi.hoisted(() => ({
  disposed: 0,
  released: 0,
  drawn: 0,
  frame: null as (() => void) | null,
  scene: null as THREE.Scene | null,
  camera: null as THREE.OrthographicCamera | null,
}));

/**
 * Only the renderer is faked. jsdom has no GL context, but every other three.js object works here, so the shape the
 * component actually built can be read back off the scene instead of counted through a stub.
 */
vi.mock("three", async (importOriginal) => ({
  ...(await importOriginal<typeof import("three")>()),
  WebGLRenderer: class {
    domElement = document.createElement("canvas");
    setSize() {}
    setPixelRatio() {}
    setClearColor() {}
    setAnimationLoop(frame: (() => void) | null) {
      gpu.frame = frame;
    }
    render(scene: THREE.Scene, camera: THREE.OrthographicCamera) {
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

type Props = ComponentProps<typeof StreamCanvas>;

const DAY = 86_400_000;
const size = { width: 800, height: 500 };

const props: Props = {
  marks: [],
  size,
  camera: RESTING_CAMERA,
  highlight: { tags: null, marks: null },
  chosen: null,
};

function mark(
  signalId: number,
  occurredAt: number,
  about: Partial<Pick<PlacedContribution, "slot" | "sourceType" | "tag">> = {},
): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x: 0,
    y: 0,
    slot: 0,
    sourceType: "SLOT",
    userId: "u-1",
    occurredAt,
    reach: null,
    ...about,
  };
}

function draw(overrides: Partial<Props> = {}) {
  return render(<StreamCanvas {...props} {...overrides} />);
}

/** Drives the frame a change asked for, if any, and reads back the scene the fake renderer was last handed. */
function frame(): THREE.Scene {
  act(() => gpu.frame?.());
  return gpu.scene!;
}

function ribbonsOf(scene: THREE.Scene): THREE.Mesh[] {
  return scene.children.filter(
    (child): child is THREE.Mesh => child instanceof THREE.Mesh,
  );
}

function edgesOf(scene: THREE.Scene): THREE.Line[] {
  return scene.children.filter(
    (child): child is THREE.Line => child instanceof THREE.Line,
  );
}

function pointsOf(scene: THREE.Scene): THREE.Points[] {
  return scene.children.filter(
    (child): child is THREE.Points => child instanceof THREE.Points,
  );
}

function colourOf(ribbon: THREE.Mesh): string {
  return (ribbon.material as THREE.MeshBasicMaterial).color.getHexString();
}

function verticalsOf(object: THREE.Mesh | THREE.Points): number[] {
  const position = object.geometry.getAttribute("position");
  return Array.from({ length: position.count }, (_, i) => position.getY(i));
}

function horizontalsOf(object: THREE.Mesh | THREE.Points): number[] {
  const position = object.geometry.getAttribute("position");
  return Array.from({ length: position.count }, (_, i) => position.getX(i));
}

describe("what StreamCanvas draws", () => {
  beforeEach(() => {
    gpu.disposed = 0;
    gpu.released = 0;
    gpu.drawn = 0;
    gpu.frame = null;
    gpu.scene = null;
    gpu.camera = null;
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    document.documentElement.style.removeProperty("--ring-teal-a");
    document.documentElement.className = "";
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("gives every topic its own ribbon", () => {
    draw({
      marks: [
        mark(1, 0, { tag: "kafka" }),
        mark(2, DAY, { slot: 1, tag: "rust" }),
        mark(3, 2 * DAY, { slot: 1, tag: "rust" }),
      ],
    });

    expect(ribbonsOf(frame())).toHaveLength(2);
  });

  /** Filled alone, neighbouring bands of one colour read as a single shape; the edge is what says how many things
   *  a community was working on at once. */
  it("draws each ribbon its own upper edge", () => {
    draw({
      marks: [mark(1, 0, { tag: "kafka" }), mark(2, DAY, { tag: "rust" })],
    });
    const scene = frame();

    expect(edgesOf(scene)).toHaveLength(ribbonsOf(scene).length);
  });

  it("paints each neighbourhood in the accent the theme gives it", () => {
    document.documentElement.style.setProperty("--ring-teal-a", "#0f8f8f");

    draw({
      marks: [
        mark(1, 0, { tag: "kafka" }),
        mark(2, DAY, { slot: 1, tag: "rust" }),
      ],
    });
    const [teal, other] = ribbonsOf(frame()).map(colourOf);

    expect(teal).toBe("0f8f8f");
    expect(other).not.toBe("0f8f8f");
  });

  /** The stream resolves its accents out of the cascade too, so it goes stale on a theme switch the same way. */
  it("repaints when the theme is switched under it", async () => {
    document.documentElement.style.setProperty("--ring-teal-a", "#0f8f8f");
    draw({ marks: [mark(1, 0, { tag: "kafka" })] });
    expect(colourOf(ribbonsOf(frame())[0]!)).toBe("0f8f8f");

    document.documentElement.style.setProperty("--ring-teal-a", "#2bc4bf");
    await switchTheme("theme-dark");

    expect(colourOf(ribbonsOf(frame())[0]!)).toBe("2bc4bf");
  });

  // Without this, changing the reading beside the map changed nothing a reader could see on it.
  it("sets back a band the reading is not about, keeping its shape", () => {
    draw({
      marks: [
        mark(1, 0, { tag: "kafka" }),
        mark(2, DAY, { slot: 1, tag: "rust" }),
      ],
      highlight: { tags: new Set(["kafka"]), marks: null },
    });
    const [lit, dim] = ribbonsOf(frame());

    const opacityOf = (band: THREE.Mesh) =>
      (band.material as THREE.MeshBasicMaterial).opacity;
    expect(opacityOf(dim!)).toBeLessThan(opacityOf(lit!));
    expect(verticalsOf(dim!)).not.toHaveLength(0);
  });

  it("paints every band in full when the panel is about the whole map", () => {
    draw({ marks: [mark(1, 0, { tag: "kafka" })] });
    const full = (ribbonsOf(frame())[0]!.material as THREE.MeshBasicMaterial)
      .opacity;

    draw({
      marks: [mark(1, 0, { tag: "kafka" })],
      highlight: { tags: new Set(["kafka"]), marks: null },
    });

    expect(
      (ribbonsOf(frame())[0]!.material as THREE.MeshBasicMaterial).opacity,
    ).toBe(full);
  });

  /** Not just the silhouette: a stack resting on a floor also spans an equal distance up and down at its peak. */
  it("centres every bin on the horizontal axis rather than resting the stack on a floor", () => {
    draw({ marks: [mark(1, 0), mark(2, 5 * DAY), mark(3, 5 * DAY)] });
    const ys = verticalsOf(ribbonsOf(frame())[0]!);
    const offAxis = ys
      .filter((_, index) => index % 2 === 0)
      .map((low, bin) => Math.abs(low + ys[bin * 2 + 1]!));

    expect(Math.max(...offAxis)).toBeLessThan(0.05);
  });

  it("never draws past the edge of the canvas", () => {
    draw({ marks: [mark(1, 0), mark(2, 5 * DAY), mark(3, 5 * DAY)] });
    const ys = ribbonsOf(frame()).flatMap(verticalsOf);

    expect(Math.max(...ys)).toBeLessThan(size.height / 2);
  });

  /** Bled to the very edges the shape has no beginning and no end, and reads as a texture rather than a history. */
  it("starts and stops the bands clear of the side edges", () => {
    draw({ marks: [mark(1, 0), mark(2, 5 * DAY), mark(3, 5 * DAY)] });
    const xs = ribbonsOf(frame()).flatMap(horizontalsOf);

    expect(Math.max(...xs)).toBeCloseTo(size.width / 2 - PAD_X, 9);
    expect(Math.min(...xs)).toBeCloseTo(-size.width / 2 + PAD_X, 9);
  });

  it("runs time along the horizontal axis, oldest first", () => {
    draw({ marks: [mark(1, 0), mark(2, 100 * DAY)] });
    const [oldest, newest] = horizontalsOf(pointsOf(frame())[0]!);

    expect(oldest!).toBeLessThan(newest!);
  });

  it("draws the contributions themselves, one glyph kind per points object", () => {
    draw({ marks: [mark(1, 0), mark(2, DAY, { sourceType: "ARTIFACT" })] });

    expect(glyphsOf(frame())).toHaveLength(2);
  });

  /** The glyph pass, not the rim drawn behind it: both are `Points` at the same places, and the rim is drawn first. */
  function glyphsOf(scene: THREE.Scene): THREE.Points[] {
    return pointsOf(scene).filter((points) => points.renderOrder === 1);
  }

  it("lifts the glyphs clear of the ribbons they sit on", () => {
    draw({ marks: [mark(1, 0), mark(2, 5 * DAY)] });
    const scene = frame();

    const depth = pointsOf(scene)[0]!.geometry.getAttribute("position").getZ(0);

    expect(depth).toBeGreaterThan(0);
  });

  /**
   * The glyph is chosen for the pixels the mark draws at, which is the same number the material is sized in. They
   * were once 12 and 7, which would have painted a fallback shape at a size where the real icon is legible had the
   * threshold ever moved between them.
   */
  it("picks its glyph for the size it actually draws at", () => {
    draw({ marks: [mark(1, 0)] });
    const material = pointsOf(frame())[0]!.material as THREE.PointsMaterial;
    const detail = detailFor(markPxFor(size, RESTING_CAMERA.zoom));

    expect(material.map!.offset.x).toBeCloseTo(
      ATLAS_ORDER.indexOf(tileFor("SLOT", detail)) / ATLAS_ORDER.length,
      9,
    );
  });
});
