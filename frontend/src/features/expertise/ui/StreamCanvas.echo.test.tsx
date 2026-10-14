import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { RESTING_CAMERA } from "@/features/expertise/ui/stream-layout";
import {
  BAND_OPACITY,
  DIM_OPACITY,
} from "@/features/expertise/ui/stream-paint";
import { StreamCanvas } from "@/features/expertise/ui/StreamCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";

const gpu = vi.hoisted(() => ({
  frame: null as (() => void) | null,
  scene: null as THREE.Scene | null,
}));

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

const DAY = 86_400_000;

const mark = (
  signalId: number,
  tag: string,
  occurredAt: number,
  slot: number,
): PlacedContribution => ({
  signalId,
  sourceType: "SLOT",
  tag,
  x: 0,
  y: 0,
  slot,
  userId: "u-1",
  occurredAt,
  reach: null,
});

const marks = [
  mark(1, "kafka", 0, 0),
  mark(2, "kafka", 10 * DAY, 0),
  mark(3, "rust", 5 * DAY, 1),
  mark(4, "rust", 60 * DAY, 1),
  mark(5, "rust", 120 * DAY, 1),
];

const whole: DrawnHighlight = {
  highlight: { tags: null, marks: null },
  transition: "snap",
};
const echoing: DrawnHighlight = {
  highlight: { tags: new Set(["kafka"]), marks: new Set([1, 2]) },
  transition: "echo",
};

const props: ComponentProps<typeof StreamCanvas> = {
  marks,
  size: { width: 800, height: 500 },
  camera: RESTING_CAMERA,
  drawn: whole,
  chosen: null,
};

const frames = new Map<number, FrameRequestCallback>();
let now = 0;
let handle = 0;

function advance(ms: number) {
  now += ms;
  const due = [...frames.values()];
  frames.clear();
  act(() => due.forEach((frame) => frame(now)));
}

function bands(): THREE.Mesh[] {
  act(() => gpu.frame?.());
  return gpu.scene!.children.filter(
    (child): child is THREE.Mesh => child instanceof THREE.Mesh,
  );
}

function alphasOf(band: THREE.Mesh): number[] {
  const colour = band.geometry.getAttribute("color");
  return Array.from({ length: colour.count }, (_, i) => colour.getW(i));
}

function colours(): Float32Array[] {
  return bands().map((band) =>
    Float32Array.from(band.geometry.getAttribute("color").array),
  );
}

function settledIn(drawn: DrawnHighlight): Float32Array[] {
  const { unmount } = render(<StreamCanvas {...props} drawn={drawn} />);
  const settled = colours();
  unmount();
  return settled;
}

describe("StreamCanvas echo", () => {
  beforeEach(() => {
    Object.assign(gpu, { frame: null, scene: null });
    frames.clear();
    now = 0;
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
    vi.spyOn(performance, "now").mockImplementation(() => now);
    vi.stubGlobal("requestAnimationFrame", (frame: FrameRequestCallback) => {
      frames.set(++handle, frame);
      return handle;
    });
    vi.stubGlobal("cancelAnimationFrame", (id: number) => frames.delete(id));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("carries a band part way to set back mid-echo, on the band it already drew", () => {
    const { rerender } = render(<StreamCanvas {...props} />);
    const before = bands();

    rerender(<StreamCanvas {...props} drawn={echoing} />);
    advance(ECHO.durationMs / 2);
    const during = bands();

    const between = during
      .flatMap(alphasOf)
      .some(
        (alpha) =>
          alpha > Math.fround(DIM_OPACITY) && alpha < Math.fround(BAND_OPACITY),
      );
    expect(between).toBe(true);
    expect(during).toHaveLength(before.length);
    expect(new Set([...before, ...during]).size).toBe(before.length);
  });

  it("settles on exactly the colours a snap paints", () => {
    const target = settledIn({ ...echoing, transition: "snap" });
    const { rerender } = render(<StreamCanvas {...props} />);

    rerender(<StreamCanvas {...props} drawn={echoing} />);
    advance(ECHO.durationMs / 2);
    advance(ECHO.durationMs + ECHO.twinkleMs);

    expect(colours()).toStrictEqual(target);
    expect(frames.size).toBe(0);
  });

  it("leaves no frame queued when it goes away mid-echo", () => {
    const { rerender, unmount } = render(<StreamCanvas {...props} />);
    rerender(<StreamCanvas {...props} drawn={echoing} />);
    advance(500);
    expect(frames.size).toBe(1);

    unmount();
    expect(frames.size).toBe(0);
  });

  it("never starts an echo because it mounted", () => {
    render(<StreamCanvas {...props} drawn={echoing} />);

    expect(frames.size).toBe(0);
  });
});
