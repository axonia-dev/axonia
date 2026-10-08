import type { WebGLRenderer } from "three";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { drawOnDemand } from "@/features/expertise/ui/draw-on-demand";

type Notify = (entries: { isIntersecting: boolean }[]) => void;

const screen: { notify: Notify } = { notify: () => {} };

class FakeRenderer {
  domElement = document.createElement("canvas");
  loop: (() => void) | null = null;
  setAnimationLoop(loop: (() => void) | null) {
    this.loop = loop;
  }
}

function setUp() {
  const renderer = new FakeRenderer();
  const draw = vi.fn();
  const drawing = drawOnDemand(
    renderer as unknown as WebGLRenderer,
    document.createElement("div"),
    draw,
  );
  /** Runs as many animation frames as the browser would, which is none once the loop has stopped. */
  const frames = (count: number) => {
    for (let i = 0; i < count; i++) renderer.loop?.();
  };
  return { renderer, draw, drawing, frames };
}

describe("drawOnDemand", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        constructor(notify: Notify) {
          screen.notify = notify;
        }
        observe() {}
        disconnect() {}
      },
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("draws the first frame, then nothing until asked", () => {
    const { draw, frames } = setUp();

    frames(5);

    expect(draw).toHaveBeenCalledOnce();
  });

  it("draws every request made before a frame as that one frame", () => {
    const { draw, drawing, frames } = setUp();
    frames(1);

    drawing.request();
    drawing.request();
    drawing.request();
    frames(5);

    expect(draw).toHaveBeenCalledTimes(2);
  });

  it("holds a request made off screen until the map is back", () => {
    const { draw, drawing, frames } = setUp();
    frames(1);

    screen.notify([{ isIntersecting: false }]);
    drawing.request();
    frames(5);
    expect(draw).toHaveBeenCalledOnce();

    screen.notify([{ isIntersecting: true }]);
    frames(5);
    expect(draw).toHaveBeenCalledTimes(2);
  });

  it("does not draw for coming back on screen when nothing changed", () => {
    const { draw, frames } = setUp();
    frames(1);

    screen.notify([{ isIntersecting: false }]);
    screen.notify([{ isIntersecting: true }]);
    frames(5);

    expect(draw).toHaveBeenCalledOnce();
  });

  it("draws again when the browser gives a lost context back", () => {
    const { renderer, draw, frames } = setUp();
    frames(1);

    renderer.domElement.dispatchEvent(new Event("webglcontextrestored"));
    frames(5);

    expect(draw).toHaveBeenCalledTimes(2);
  });

  it("asks for nothing once stopped", () => {
    const { renderer, drawing } = setUp();

    drawing.stop();
    renderer.domElement.dispatchEvent(new Event("webglcontextrestored"));

    expect(renderer.loop).toBeNull();
  });

  it("starts no loop for a request made once stopped", () => {
    const { renderer, draw, drawing, frames } = setUp();
    frames(1);

    drawing.stop();
    drawing.request();
    screen.notify([{ isIntersecting: true }]);

    expect(renderer.loop).toBeNull();
    expect(draw).toHaveBeenCalledOnce();
  });
});
