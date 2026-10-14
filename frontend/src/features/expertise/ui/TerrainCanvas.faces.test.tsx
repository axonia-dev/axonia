import { act, render } from "@testing-library/react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { FACE_SET_BACK_OPACITY } from "@/features/expertise/ui/face-sprites";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { TerrainCanvas } from "@/features/expertise/ui/TerrainCanvas";
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

function mark(signalId: number, tag: string, x: number): PlacedContribution {
  return {
    signalId,
    sourceType: "SLOT",
    tag,
    x,
    y: 90,
    slot: 0,
    userId: `u-${signalId}`,
    occurredAt: 0,
    reach: 90,
  };
}

describe("TerrainCanvas faces", () => {
  beforeEach(() => {
    Object.assign(gpu, { frame: null, scene: null });
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sets a face back with its mark when the map is about other ground", () => {
    const marks = [mark(1, "kafka", 100), mark(2, "rust", 600)];
    render(
      <TerrainCanvas
        surface={{
          field: buildHeightfield(marks, size),
          size,
          projection: "flat",
        }}
        view={{ zoom: 1, size, projection: "flat" }}
        marks={marks}
        topics={[]}
        people={
          new Map([
            [
              "u-1",
              { userId: "u-1", displayName: "Ada Byron", avatarUrl: null },
            ],
            [
              "u-2",
              { userId: "u-2", displayName: "Alan Kay", avatarUrl: null },
            ],
          ])
        }
        faced={new Set([1, 2])}
        drawn={{
          highlight: { tags: new Set(["kafka"]), marks: new Set([1]) },
          transition: "snap",
        }}
        chosen={null}
      />,
    );
    act(() => gpu.frame?.());

    const sprites = gpu.scene!.children.filter(
      (o): o is THREE.Sprite => o instanceof THREE.Sprite,
    );
    sprites.sort((a, b) => a.position.x - b.position.x);
    const opacities = sprites.map((s) => s.material.opacity);

    // A tint holds float32, which is what the GPU reads the uniform as anyway.
    expect(opacities[0]).toBe(1);
    expect(opacities[1]).toBe(Math.fround(FACE_SET_BACK_OPACITY));
  });
});
