import * as THREE from "three";

import {
  cameraEye,
  FAR,
  FOV,
  fovOf,
  NEAR,
  targetOf,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";

export const BOX = { width: 800, height: 500 };

export function viewOf(
  zoom: number,
  projection: TerrainView["projection"],
): TerrainView {
  return { zoom, size: BOX, projection };
}

/** The camera the renderer builds, assembled the way `TerrainCanvas` assembles it, so the two cannot drift apart
 *  unnoticed: the same eye, the same aim and the same lens. */
function threeCamera(view: TerrainView): THREE.PerspectiveCamera {
  const camera = new THREE.PerspectiveCamera(
    FOV,
    view.size.width / view.size.height,
    NEAR,
    FAR,
  );
  const eye = cameraEye(view);
  const at = targetOf(view);
  camera.position.set(eye.x, eye.y, eye.z);
  camera.lookAt(at.x, at.y, at.z);
  camera.fov = fovOf(view);
  camera.updateProjectionMatrix();
  camera.updateMatrixWorld(true);
  return camera;
}

export function threeSpot(point: THREE.Vector3, view: TerrainView) {
  const ndc = point.clone().project(threeCamera(view));
  return { left: 0.5 + ndc.x / 2, top: 0.5 - ndc.y / 2 };
}
