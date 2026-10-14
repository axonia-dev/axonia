import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
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

const props: ComponentProps<typeof TerrainCanvas> = {
  surface: { field: buildHeightfield(marks, size), size, projection: "flat" },
  view: { zoom: 1, size, projection: "flat" },
  marks,
  topics: [topic("kafka", 200), topic("rust", 600)],
  people: new Map(),
  faced: new Set(),
  drawn: { highlight: { tags: null, marks: null }, transition: "snap" },
  chosen: null,
};

function groundOf(): THREE.Mesh {
  act(() => gpu.frame?.());
  return gpu.scene!.children.find(
    (child): child is THREE.Mesh => child instanceof THREE.Mesh,
  )!;
}

describe("TerrainCanvas repainting", () => {
  beforeEach(() => {
    Object.assign(gpu, { frame: null, scene: null });
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** A heightfield is tens of thousands of shaded vertices, and a highlight moves only their colours. */
  it("repaints the ground a highlight moves rather than rebuilding it", () => {
    const { rerender } = render(<TerrainCanvas {...props} />);
    const before = groundOf();
    const coloursBefore = Float32Array.from(
      before.geometry.getAttribute("color").array,
    );

    rerender(
      <TerrainCanvas
        {...props}
        drawn={{
          highlight: { tags: new Set(["kafka"]), marks: null },
          transition: "snap",
        }}
      />,
    );
    const after = groundOf();

    expect(after).toBe(before);
    expect(after.geometry.getAttribute("color").array).not.toStrictEqual(
      coloursBefore,
    );
  });
});
