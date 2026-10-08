import { act, render } from "@testing-library/react";
import type { ComponentProps } from "react";
import type * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { RESTING_CAMERA } from "@/features/expertise/ui/stream-layout";
import { StreamCanvas } from "@/features/expertise/ui/StreamCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";

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

type Props = ComponentProps<typeof StreamCanvas>;

const DAY = 86_400_000;
const START = Date.UTC(2026, 0, 15, 12);

const marks: PlacedContribution[] = [1, 2, 3].map((signalId) => ({
  signalId,
  sourceType: "SLOT",
  tag: "kafka",
  x: 0,
  y: 0,
  slot: 0,
  userId: "u-1",
  occurredAt: START + signalId * 40 * DAY,
  reach: 40,
}));

const props: Props = {
  marks,
  size: { width: 800, height: 500 },
  camera: RESTING_CAMERA,
  highlight: { tags: null, marks: null },
  chosen: null,
};

/** Runs as many animation frames as a browser would, which is none while nothing has asked for one. */
function frames(count = 5) {
  act(() => {
    for (let i = 0; i < count; i++) gpu.frame?.();
  });
}

/** The stream draws when something changes and at no other time, like the terrain beside it. */
describe("StreamCanvas draws on demand", () => {
  beforeEach(() => {
    gpu.drawn = 0;
    gpu.frame = null;
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("draws once, and not again while nothing changes", () => {
    render(<StreamCanvas {...props} />);

    frames();

    expect(gpu.drawn).toBe(1);
  });

  it("draws one frame for a camera that moved, and one for a mark chosen", () => {
    const { rerender } = render(<StreamCanvas {...props} />);
    frames();

    const closer = { zoom: 3, pan: { x: 40, y: 0 } };
    rerender(<StreamCanvas {...props} camera={closer} />);
    frames();
    rerender(<StreamCanvas {...props} camera={closer} chosen={2} />);
    frames();

    expect(gpu.drawn).toBe(3);
  });

  /** A pan alone moves the frame and leaves the scene as it was built, so only the camera can ask for the frame. */
  it("draws one frame for a camera that panned at the same zoom", () => {
    const { rerender } = render(<StreamCanvas {...props} />);
    frames();

    rerender(
      <StreamCanvas
        {...props}
        camera={{ ...RESTING_CAMERA, pan: { x: 40, y: 0 } }}
      />,
    );
    frames();

    expect(gpu.drawn).toBe(2);
  });
});
