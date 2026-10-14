import * as THREE from "three";

import {
  type DrawOnDemand,
  drawOnDemand,
} from "@/features/expertise/ui/draw-on-demand";
import { FAR, FOV, NEAR } from "@/features/expertise/ui/terrain-camera";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";
import { rendererPixelRatio } from "@/features/expertise/ui/webgl-support";

/** What the scene is drawn with, which outlives every rebuild of what is drawn in it. */
export interface Gpu {
  renderer: THREE.WebGLRenderer;
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  /** Asks for the next frame: whatever changes the picture says so, and nothing is drawn otherwise. */
  request: DrawOnDemand["request"];
  onScreen: DrawOnDemand["onScreen"];
}

/**
 * The renderer, and nothing that depends on where the camera stands or on what the contributions are.
 *
 * A WebGL context is a scarce resource - a browser keeps about sixteen of them alive and starts losing the oldest
 * past that - so one is taken per box, not per rebuild, and `close` hands it straight back. Built on a zoom, which is
 * a dozen wheel clicks from one end of the range to the other, a renderer per rebuild spends the whole budget in
 * seconds and the context lost is the one on screen.
 */
export function openGpu(
  host: HTMLElement,
  size: GraphSize,
): { gpu: Gpu; close: () => void } {
  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    alpha: true,
    stencil: true,
  });
  renderer.setPixelRatio(rendererPixelRatio());
  renderer.setSize(size.width, size.height);
  host.append(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(
    FOV,
    size.width / size.height,
    NEAR,
    FAR,
  );

  // Intensities are in the renderer's own units, which are physical: since three r155 a light is no longer a
  // plain multiplier on the albedo, and it is short by a factor of PI. Read as multipliers, 0.86 and 0.4 drew
  // the globe at under half the colour the ramp asked for - a grey smudge where the flat reading, which is
  // unlit, was a map. Stated this way the two sum to the full colour at the brightest face, and the globe keeps
  // the gradient that tells a sphere from a disc.
  const FULL = Math.PI;
  scene.add(new THREE.AmbientLight(0xffffff, 0.88 * FULL));
  const key = new THREE.DirectionalLight(0xffffff, 0.12 * FULL);
  key.position.set(-0.4, 0.86, 0.32);
  scene.add(key);

  const drawing = drawOnDemand(renderer, host, () =>
    renderer.render(scene, camera),
  );

  return {
    gpu: {
      renderer,
      scene,
      camera,
      request: drawing.request,
      onScreen: drawing.onScreen,
    },
    close: () => {
      drawing.stop();
      // `dispose` frees what three.js allocated and leaves the context itself to the collector, which may be a
      // second or a minute away. Losing it on purpose gives it back now, which is the point of taking only one.
      renderer.forceContextLoss();
      renderer.dispose();
      renderer.domElement.remove();
    },
  };
}
