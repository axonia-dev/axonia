import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
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

const size = { width: 800, height: 500 };

const topic = (tag: string, x: number): PlacedTopic => ({
  tag,
  x,
  y: 250,
  r: 20,
  declarations: 0,
  practised: true,
});

const mark = (
  signalId: number,
  tag: string,
  x: number,
  slot: number,
): PlacedContribution => ({
  signalId,
  sourceType: "SLOT",
  tag,
  x,
  y: 250,
  slot,
  userId: "u-1",
  occurredAt: 0,
  reach: null,
});

const marks = [mark(1, "kafka", 200, 0), mark(2, "rust", 600, 1)];
const whole: DrawnHighlight = {
  highlight: { tags: null, marks: null },
  transition: "snap",
};
const lit: DrawnHighlight["highlight"] = {
  tags: new Set(["kafka"]),
  marks: new Set([1]),
};

const props: ComponentProps<typeof TerrainCanvas> = {
  surface: { field: buildHeightfield(marks, size), size, projection: "flat" },
  view: { zoom: 1, size, projection: "flat" },
  marks,
  topics: [topic("kafka", 200), topic("rust", 600)],
  people: new Map(),
  faced: new Set(),
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

function groundColours(): Float32Array {
  act(() => gpu.frame?.());
  const ground = gpu.scene!.children.find(
    (child): child is THREE.Mesh => child instanceof THREE.Mesh,
  )!;
  return Float32Array.from(ground.geometry.getAttribute("color").array);
}

function settledIn(drawn: DrawnHighlight): Float32Array {
  const { unmount } = render(<TerrainCanvas {...props} drawn={drawn} />);
  const colours = groundColours();
  unmount();
  return colours;
}

describe("TerrainCanvas echo", () => {
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

  it("carries the ground from its full colours to the set-back ones and settles on them exactly", () => {
    const full = settledIn(whole);
    const target = settledIn({ highlight: lit, transition: "snap" });
    const { rerender } = render(<TerrainCanvas {...props} />);
    expect(frames.size).toBe(0);

    rerender(
      <TerrainCanvas
        {...props}
        drawn={{ highlight: lit, transition: "echo" }}
      />,
    );
    advance(ECHO.durationMs / 2);
    const midway = groundColours();
    const between = midway.some((value, i) => {
      const [low, high] = [full[i]!, target[i]!].sort((a, b) => a - b);
      return value > low! && value < high!;
    });
    expect(between).toBe(true);

    advance(ECHO.durationMs + ECHO.twinkleMs);
    expect(groundColours()).toStrictEqual(target);
    expect(frames.size).toBe(0);
  });

  it("leaves no frame queued when it goes away mid-echo", () => {
    const { rerender, unmount } = render(<TerrainCanvas {...props} />);
    rerender(
      <TerrainCanvas
        {...props}
        drawn={{ highlight: lit, transition: "echo" }}
      />,
    );
    advance(500);
    expect(frames.size).toBe(1);
    unmount();
    expect(frames.size).toBe(0);
  });

  it("settles at once when the ground is rebuilt mid-echo, rings and all", () => {
    const target = settledIn({ highlight: lit, transition: "snap" });
    const echoing = { highlight: lit, transition: "echo" } as const;
    const { rerender } = render(<TerrainCanvas {...props} />);
    rerender(<TerrainCanvas {...props} drawn={echoing} />);
    advance(500);

    rerender(
      <TerrainCanvas
        {...props}
        surface={{ ...props.surface }}
        drawn={echoing}
      />,
    );
    expect(frames.size).toBe(0);
    expect(groundColours()).toStrictEqual(target);
    const rings = gpu.scene!.children.filter(
      (child) => child instanceof THREE.LineLoop && child.visible,
    );
    expect(rings).toHaveLength(0);
  });

  it("never starts an echo because it mounted", () => {
    render(
      <TerrainCanvas
        {...props}
        drawn={{ highlight: lit, transition: "echo" }}
      />,
    );
    expect(frames.size).toBe(0);
  });
});
