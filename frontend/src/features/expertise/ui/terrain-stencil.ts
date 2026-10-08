import * as THREE from "three";

/**
 * Everything standing on the terrain floats a little above it - a mark by its lift, a face by more - and on the globe
 * that lift carries a mark near the limb past the planet's silhouette, where there is no ground behind it to hide it
 * and it hangs on the page. So the ground stamps the stencil wherever it is drawn, and what stands on it is drawn
 * only there: cut at the limb exactly where the ground ends, whichever hemisphere it is on.
 */
const GROUND = 1;

/** Drawn before anything that stands on it, so the stamp is down by the time they test for it. */
const GROUND_FIRST = -1;

type Drawn = THREE.Object3D & { material: THREE.Material | THREE.Material[] };

function materialsOf(object: Drawn): THREE.Material[] {
  return Array.isArray(object.material) ? object.material : [object.material];
}

export function stampGround(objects: readonly Drawn[]): void {
  for (const object of objects) {
    object.renderOrder = GROUND_FIRST;
    for (const material of materialsOf(object)) {
      material.stencilWrite = true;
      material.stencilRef = GROUND;
      material.stencilFunc = THREE.AlwaysStencilFunc;
      material.stencilZPass = THREE.ReplaceStencilOp;
    }
  }
}

export function standOnGround(objects: readonly Drawn[]): void {
  for (const object of objects) {
    for (const material of materialsOf(object)) {
      material.stencilWrite = true;
      material.stencilRef = GROUND;
      material.stencilFunc = THREE.EqualStencilFunc;
      material.stencilZPass = THREE.KeepStencilOp;
    }
  }
}
