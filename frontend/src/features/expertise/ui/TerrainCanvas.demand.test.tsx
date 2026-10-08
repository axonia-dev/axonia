import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import type * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";
import { switchTheme } from "@/test/theme";

const gpu = vi.hoisted(() => ({
  drawn: 0,
  frame: null as (() => void) | null,
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
    render(_scene: THREE.Scene, _camera: THREE.Camera) {
      gpu.drawn += 1;
    }
    forceContextLoss() {}
    dispose() {}
  },
}));

type Props = ComponentProps<typeof TerrainCanvas>;

const size = { width: 800, height: 500 };

const marks: PlacedContribution[] = [1, 2].map((signalId) => ({
  signalId,
  sourceType: "SLOT",
  tag: "kafka",
  x: 120 + signalId * 40,
  y: 90,
  slot: 0,
  userId: "u-1",
  occurredAt: 0,
  reach: 40,
}));

const props: Props = {
  surface: {
    field: buildHeightfield(marks, size),
    size,
    projection: "globe",
  },
  view: { zoom: 1, size, projection: "globe" },
  marks,
  topics: [],
  people: new Map(),
  faced: new Set(),
  highlight: { tags: null, marks: null },
  chosen: null,
};

/** Runs as many animation frames as a browser would, which is none while nothing has asked for one. */
function frames(count = 5) {
  act(() => {
    for (let i = 0; i < count; i++) gpu.frame?.();
  });
}

/**
 * The map draws when something changes and at no other time. Drawn every frame, a picture that had not moved kept a
 * CPU busy whenever WebGL ran in software, which is every headless browser and some real machines.
 */
describe("TerrainCanvas draws on demand", () => {
  beforeEach(() => {
    gpu.drawn = 0;
    gpu.frame = null;
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.documentElement.className = "";
  });

  it("draws once, and not again while nothing changes", () => {
    render(<TerrainCanvas {...props} />);

    frames();

    expect(gpu.drawn).toBe(1);
  });

  it("draws one frame for a camera that moved", () => {
    const { rerender } = render(<TerrainCanvas {...props} />);
    frames();

    rerender(
      <TerrainCanvas
        {...props}
        view={{ ...props.view, zoom: 2, spin: { yaw: 30, pitch: 10 } }}
      />,
    );
    frames();

    expect(gpu.drawn).toBe(2);
  });

  it("draws one frame for a mark chosen, and one for the drawing's subject", () => {
    const { rerender } = render(<TerrainCanvas {...props} />);
    frames();

    rerender(<TerrainCanvas {...props} chosen={1} />);
    frames();
    rerender(
      <TerrainCanvas
        {...props}
        chosen={1}
        highlight={{ tags: new Set(["kafka"]), marks: null }}
      />,
    );
    frames();

    expect(gpu.drawn).toBe(3);
  });

  /** Faces stand on the ground layer alone, so a face that resolves changes nothing the marks or the camera see. */
  it("draws one frame when a face resolves", () => {
    const { rerender } = render(<TerrainCanvas {...props} />);
    frames();

    rerender(
      <TerrainCanvas
        {...props}
        faced={new Set([1])}
        people={
          new Map([
            ["u-1", { userId: "u-1", displayName: "Ada", avatarUrl: null }],
          ])
        }
      />,
    );
    frames();

    expect(gpu.drawn).toBe(2);
  });

  it("draws one frame when the page changes theme", async () => {
    render(<TerrainCanvas {...props} />);
    frames();

    await switchTheme("theme-dark");
    frames();

    expect(gpu.drawn).toBe(2);
  });
});
