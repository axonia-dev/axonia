import type { WebGLRenderer } from "three";

export interface DrawOnDemand {
  /** Asks for one frame. Any number of requests before it is drawn are the same frame. */
  request: () => void;
  stop: () => void;
}

/**
 * Draws the scene once after something asks for it, and not again until something else does.
 *
 * Nothing in the map moves by itself: the camera, the layers and the textures change only when the reader or the data
 * changes them, so a frame drawn when nothing changed is the same frame again. Drawn every frame regardless, the map
 * kept a CPU busy whenever WebGL ran in software, and slowed every other thing on the page with it. A canvas keeps
 * showing the last frame it was given, so drawing nothing is not a blank. The loop stops after each frame and only
 * runs while the map is on screen; a request made while it is off screen is drawn when it comes back.
 */
export function drawOnDemand(
  renderer: WebGLRenderer,
  host: Element,
  draw: () => void,
): DrawOnDemand {
  let visible = true;
  let wanted = true;
  let stopped = false;

  const frame = () => {
    renderer.setAnimationLoop(null);
    wanted = false;
    draw();
  };
  // A stopped renderer is being disposed, so a late request must not start a loop on it.
  const wake = () => {
    if (wanted && visible && !stopped) renderer.setAnimationLoop(frame);
  };
  const request = () => {
    wanted = true;
    wake();
  };

  const observer = new IntersectionObserver(
    ([entry]) => {
      visible = entry?.isIntersecting ?? false;
      if (visible) wake();
      else renderer.setAnimationLoop(null);
    },
    { threshold: 0.05 },
  );
  observer.observe(host);
  // A context the browser took back and then restored comes back blank, with nothing else having changed.
  const canvas = renderer.domElement;
  canvas.addEventListener("webglcontextrestored", request);
  wake();

  return {
    request,
    stop: () => {
      stopped = true;
      observer.disconnect();
      canvas.removeEventListener("webglcontextrestored", request);
      renderer.setAnimationLoop(null);
    },
  };
}
