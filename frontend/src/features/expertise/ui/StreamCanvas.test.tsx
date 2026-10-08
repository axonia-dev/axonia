import { act, render, screen } from "@testing-library/react";
import type { ComponentProps } from "react";
import * as THREE from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  RESTING_CAMERA,
  streamFrustum,
} from "@/features/expertise/ui/stream-layout";
import { StreamCanvas } from "@/features/expertise/ui/StreamCanvas";
import * as webgl from "@/features/expertise/ui/webgl-support";

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
/** Midday UTC, so the zone the suite pins west of UTC still reads the same calendar day. */
const START = Date.UTC(2026, 0, 15, 12);

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

describe("StreamCanvas", () => {
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
    document.documentElement.style.removeProperty("--primary");
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("says so rather than rendering nothing when WebGL2 is missing", () => {
    vi.spyOn(webgl, "hasWebGl2").mockReturnValue(false);

    draw();

    expect(screen.getByRole("status")).toHaveTextContent(
      /map needs a browser with WebGL/i,
    );
  });

  it("hides the drawing from assistive technology", () => {
    const { container } = draw();

    expect(container.querySelector("[aria-hidden='true']")).not.toBeNull();
  });

  /** A mark chosen here opens the same strip the terrain opens, so it has to be as findable here as it is there. */
  it("paints the chosen mark in the map's chosen ink here too", () => {
    document.documentElement.style.setProperty("--primary", "#ff00aa");

    draw({ marks: [mark(1, START), mark(2, START + DAY)], chosen: 1 });
    const chosen = frame().children.find(
      (child): child is THREE.Points =>
        child instanceof THREE.Points && child.renderOrder === 3,
    );

    expect(
      new THREE.Color()
        .fromBufferAttribute(chosen!.geometry.getAttribute("color"), 0)
        .getHexString(),
    ).toBe("ff00aa");
  });

  /** `dispose` alone leaves the context on the document, and the browser hands out about sixteen of them. */
  it("releases the GPU context on unmount", () => {
    draw().unmount();

    expect(gpu.disposed).toBe(1);
    expect(gpu.released).toBe(1);
  });

  it("says when there is nothing to draw a history from", () => {
    draw();

    expect(
      screen.getByText(/nothing has been shared yet/i),
    ).toBeInTheDocument();
  });

  it("stops saying so once a contribution has a history to draw", () => {
    draw({ marks: [mark(1, 0)] });

    expect(screen.queryByText(/nothing has been shared yet/i)).toBeNull();
  });

  /** Nothing in the drawing says which end is old, and text painted into a canvas reaches no screen reader. */
  it("names the period the bands cover", () => {
    draw({ marks: [mark(1, START), mark(2, START + 30 * DAY)] });

    expect(screen.getByText("From 15 Jan 2026")).toBeInTheDocument();
    expect(screen.getByText("To 14 Feb 2026")).toBeInTheDocument();
  });

  it("says nothing about a period when nothing has a date to place", () => {
    draw({ marks: [mark(1, Number.NaN)] });

    expect(screen.queryByText(/^From /)).toBeNull();
  });

  /** The overlay projects onto exactly these dimensions, so the axis must ride over the drawing, never beside it. */
  it("keeps the drawing itself at exactly the size it was given", () => {
    const { container } = draw({ marks: [mark(1, START)] });

    expect(container.querySelector("[aria-hidden='true']")).toHaveStyle({
      width: `${size.width}px`,
      height: `${size.height}px`,
    });
  });

  /** The reader zooms a dozen clicks at a time; rebuilding a history under each one would spend the browser's whole
   *  budget of GL contexts and throw the picture away between them. */
  it("moves the camera on a zoom without rebuilding the bands under it", () => {
    const marks = [mark(1, 0), mark(2, 100 * DAY)];
    const { rerender } = draw({ marks });
    const built = frame();

    const closer = { zoom: 3, pan: { x: 40, y: 0 } };
    rerender(<StreamCanvas {...props} marks={marks} camera={closer} />);
    frame();

    const { left, right, top, bottom } = streamFrustum(size, closer);
    expect(gpu.camera!.left).toBe(left);
    expect(gpu.camera!.right).toBe(right);
    expect(gpu.camera!.top).toBe(top);
    expect(gpu.camera!.bottom).toBe(bottom);
    expect(gpu.scene).toBe(built);
    expect(gpu.disposed).toBe(0);
  });

  it("names only the stretch of history the camera still holds", () => {
    const marks = [mark(1, START), mark(2, START + 300 * DAY)];
    draw({ marks, camera: { zoom: 6, pan: { x: 0, y: 0 } } });

    expect(screen.queryByText("From 15 Jan 2026")).toBeNull();
    expect(screen.getByText(/^From /)).toBeInTheDocument();
  });

  it("draws only while the map is on screen", () => {
    const observer: {
      notify: (entries: { isIntersecting: boolean }[]) => void;
    } = { notify: () => {} };
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        constructor(notify: (entries: { isIntersecting: boolean }[]) => void) {
          observer.notify = notify;
        }
        observe() {}
        disconnect() {}
      },
    );
    const { rerender } = draw();

    act(() => observer.notify([{ isIntersecting: true }]));
    frame();
    act(() => observer.notify([]));
    rerender(
      <StreamCanvas {...props} camera={{ zoom: 2, pan: { x: 0, y: 0 } }} />,
    );
    frame();
    expect(gpu.drawn).toBe(1);

    act(() => observer.notify([{ isIntersecting: true }]));
    frame();
    expect(gpu.drawn).toBe(2);
  });
});
