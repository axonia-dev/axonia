import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  RESTING_CAMERA,
  type Stacked,
} from "@/features/expertise/ui/stream-layout";
import {
  BAND_OPACITY,
  bandColour,
  DIM_OPACITY,
  EDGE_OPACITY,
} from "@/features/expertise/ui/stream-paint";
import { StreamCanvas } from "@/features/expertise/ui/StreamCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";
import { switchTheme } from "@/test/theme";

const gpu = vi.hoisted(() => ({
  frame: null as (() => void) | null,
  scene: null as THREE.Scene | null,
}));

/** Only the renderer is faked: the bands the component built are read back off the real scene. */
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
    render(scene: THREE.Scene) {
      gpu.scene = scene;
    }
    forceContextLoss() {}
    dispose() {}
  },
}));

type Props = ComponentProps<typeof StreamCanvas>;

const DAY = 86_400_000;

const props: Props = {
  marks: [],
  size: { width: 800, height: 500 },
  camera: RESTING_CAMERA,
  drawn: { highlight: { tags: null, marks: null }, transition: "snap" },
  chosen: null,
};

const onKafka: Props["drawn"] = {
  highlight: { tags: new Set(["kafka"]), marks: null },
  transition: "snap",
};

function mark(
  signalId: number,
  occurredAt: number,
  about: Partial<Pick<PlacedContribution, "slot" | "tag">> = {},
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
    (child): child is THREE.Line =>
      child instanceof THREE.Line && !(child instanceof THREE.LineLoop),
  );
}

function verticalsOf(object: THREE.Mesh): number[] {
  const position = object.geometry.getAttribute("position");
  return Array.from({ length: position.count }, (_, i) => position.getY(i));
}

/** Every vertex of a band or an edge, as the colour it is drawn in and how solid it is. */
function shadesOf(object: THREE.Mesh | THREE.Line): [string, number][] {
  const colour = object.geometry.getAttribute("color");
  return Array.from({ length: colour.count }, (_, i) => [
    new THREE.Color().fromBufferAttribute(colour, i).getHexString(),
    colour.getW(i),
  ]);
}

function colourOf(ribbon: THREE.Mesh): string {
  return shadesOf(ribbon)[0]![0];
}

/** One shade, as a vertex holds it: the attribute is single precision, the expected values are not. */
const shade = (colour: THREE.Color, alpha: number): [string, number] => [
  colour.getHexString(),
  Math.fround(alpha),
];

const slotOf = (slot: number) => ({ slot }) as Stacked;

describe("how StreamCanvas paints its bands", () => {
  beforeEach(() => {
    Object.assign(gpu, { frame: null, scene: null });
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    document.documentElement.style.removeProperty("--ring-teal-a");
    document.documentElement.className = "";
    vi.restoreAllMocks();
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
      drawn: onKafka,
    });
    const scene = frame();
    const [lit, dim] = ribbonsOf(scene);
    const [litEdge, dimEdge] = edgesOf(scene);

    const every = (
      object: THREE.Mesh | THREE.Line,
      expected: [string, number],
    ) =>
      expect(new Set(shadesOf(object).map(String))).toStrictEqual(
        new Set([String(expected)]),
      );
    every(lit!, shade(bandColour(slotOf(0), true), BAND_OPACITY));
    every(dim!, shade(bandColour(slotOf(1), false), DIM_OPACITY));
    every(litEdge!, shade(bandColour(slotOf(0), true), EDGE_OPACITY));
    every(dimEdge!, shade(bandColour(slotOf(1), false), DIM_OPACITY));
    expect(verticalsOf(dim!)).not.toHaveLength(0);
  });

  it("paints every band in full when the panel is about the whole map", () => {
    draw({ marks: [mark(1, 0, { tag: "kafka" })] });
    const full = shadesOf(ribbonsOf(frame())[0]!);

    draw({
      marks: [mark(1, 0, { tag: "kafka" })],
      drawn: onKafka,
    });

    expect(shadesOf(ribbonsOf(frame())[0]!)).toStrictEqual(full);
  });
});
