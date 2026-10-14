import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { buildStream } from "@/features/expertise/ui/stream-bins";
import {
  binsFor,
  RESTING_CAMERA,
  stack,
  xOf,
} from "@/features/expertise/ui/stream-layout";
import { StreamCanvas } from "@/features/expertise/ui/StreamCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";

const gpu = vi.hoisted(() => ({
  frame: null as (() => void) | null,
  scene: null as THREE.Scene | null,
  stencil: false,
}));

vi.mock("three", async (importOriginal) => ({
  ...(await importOriginal<typeof import("three")>()),
  WebGLRenderer: class {
    domElement = document.createElement("canvas");
    constructor(options: { stencil?: boolean }) {
      gpu.stencil = options.stencil === true;
    }
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
const size = { width: 800, height: 500 };

const mark = (
  signalId: number,
  tag: string,
  occurredAt: number,
): PlacedContribution => ({
  signalId,
  sourceType: "SLOT",
  tag,
  x: 0,
  y: 0,
  slot: tag === "kafka" ? 0 : 1,
  userId: "u-1",
  occurredAt,
  reach: null,
});

// Kafka at both ends of the history and rust filling the middle, so the kafka band is a wide U and the centre of its
// marks lies in the gap above the middle of it.
const marks = [
  mark(1, "kafka", 0),
  mark(2, "kafka", DAY),
  mark(3, "kafka", 119 * DAY),
  mark(4, "kafka", 120 * DAY),
  ...Array.from({ length: 12 }, (_, n) => mark(10 + n, "rust", (50 + n) * DAY)),
];

const whole: DrawnHighlight = {
  highlight: { tags: null, marks: null },
  transition: "snap",
};
const kafka: DrawnHighlight = {
  highlight: { tags: new Set(["kafka"]), marks: new Set([1, 2, 3, 4]) },
  transition: "echo",
};

const props: ComponentProps<typeof StreamCanvas> = {
  marks,
  size,
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

function drawn<T extends THREE.Object3D>(kind: new () => T): T[] {
  act(() => gpu.frame?.());
  return gpu.scene!.children.filter(
    (child): child is T => child instanceof kind && child.visible,
  );
}

function centreOf(loop: THREE.LineLoop): { x: number; y: number } {
  const position = loop.geometry.getAttribute("position");
  let x = 0;
  let y = 0;
  for (let i = 0; i < position.count; i++) {
    x += position.getX(i);
    y += position.getY(i);
  }
  return { x: x / position.count, y: y / position.count };
}

function echoing() {
  const view = render(<StreamCanvas {...props} />);
  view.rerender(<StreamCanvas {...props} drawn={kafka} />);
  advance(100);
  return view;
}

describe("StreamCanvas rings", () => {
  beforeEach(() => {
    Object.assign(gpu, { frame: null, scene: null, stencil: false });
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

  it("starts the echo on the band, even where the band curves away from the middle of its marks", () => {
    echoing();
    const first = drawn(THREE.LineLoop)[0]!;
    const centre = centreOf(first);

    const band = stack(buildStream(marks, binsFor(size.width)), size).find(
      (b) => b.tag === "kafka",
    )!;
    const bins = band.lower.length;
    const bin = band.lower.reduce(
      (best, _, at) =>
        Math.abs(xOf(at, bins, size) - centre.x) <
        Math.abs(xOf(best, bins, size) - centre.x)
          ? at
          : best,
      0,
    );
    expect(centre.y).toBeGreaterThanOrEqual(band.lower[bin]! - 1e-3);
    expect(centre.y).toBeLessThanOrEqual(band.upper[bin]! + 1e-3);
  });

  it("draws a ring only where the bands are, as the globe draws one only on its ground", () => {
    echoing();
    const bands = drawn(THREE.Mesh);
    const rings = drawn(THREE.LineLoop);

    expect(gpu.stencil).toBe(true);
    expect(
      bands.map((b) => (b.material as THREE.Material).stencilZPass),
    ).toStrictEqual(bands.map(() => THREE.ReplaceStencilOp));
    expect(rings.length).toBeGreaterThan(0);
    expect(new Set(rings.map((r) => r.material.stencilFunc))).toStrictEqual(
      new Set([THREE.EqualStencilFunc]),
    );
  });

  it("disposes its rings and twinkles when it goes away mid-echo", () => {
    const dispose = vi.spyOn(THREE.BufferGeometry.prototype, "dispose");
    const { unmount } = echoing();
    const points = drawn(THREE.Points);
    const rings = gpu.scene!.children.filter(
      (c) => c instanceof THREE.LineLoop,
    );
    expect(points.length).toBeGreaterThan(0);

    unmount();
    const disposed = new Set(dispose.mock.contexts);
    for (const object of [...rings, ...points]) {
      expect(disposed.has((object as THREE.LineLoop).geometry)).toBe(true);
    }
  });
});
