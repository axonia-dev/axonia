import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { Vec3 } from "@/features/expertise/ui/globe-projection";
import { planeToSphere } from "@/features/expertise/ui/globe-projection";
import {
  clampSpin,
  MAX_PITCH,
  restingSpin,
  type Spin,
} from "@/features/expertise/ui/terrain-aim";
import {
  cameraEye,
  facesCamera,
  FOV,
  MARK_LIFT,
  projectToBox,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import { GLOBE_RADIUS } from "@/features/expertise/ui/terrain-surface";

const size = { width: 1000, height: 700 };

/** `spin` is genuinely absent when none is given, not present and undefined: absent is what "at rest" means. */
function globe(spin?: Spin): TerrainView {
  const at: TerrainView = { zoom: 1, size, projection: "globe" };
  return spin === undefined ? at : { ...at, spin };
}

function length(v: Vec3): number {
  return Math.hypot(v.x, v.y, v.z);
}

describe("the spin", () => {
  // Every other consumer reads the camera through cameraEye and projectToBox and never touches `spin` itself, so
  // leaving it out has to mean exactly the camera that existed before there was one.
  it("is at rest when a view does not carry one", () => {
    expect(cameraEye(globe())).toStrictEqual(
      cameraEye(globe(restingSpin("globe"))),
    );
  });

  it("turns the eye around the terrain without changing how far away it stands", () => {
    const rest = cameraEye(globe());
    const turned = cameraEye(
      globe({ yaw: 90, pitch: restingSpin("globe").pitch }),
    );

    expect(length(turned)).toBeCloseTo(length(rest), 6);
    expect(turned.x).toBeGreaterThan(0);
    expect(turned.z).toBeCloseTo(0, 6);
    expect(turned.y).toBeCloseTo(rest.y, 6);
  });

  it("lifts and drops the eye with the pitch", () => {
    expect(cameraEye(globe({ yaw: 0, pitch: 70 })).y).toBeGreaterThan(
      cameraEye(globe({ yaw: 0, pitch: 10 })).y,
    );
    expect(cameraEye(globe({ yaw: 0, pitch: -40 })).y).toBeLessThan(0);
  });

  // The basis is built against world up, and at the pole itself "right" stops being defined: without the clamp the
  // picture flips through the singularity instead of continuing to turn.
  it("stops the eye short of the pole", () => {
    expect(clampSpin({ yaw: 0, pitch: 300 }).pitch).toBe(MAX_PITCH);
    expect(clampSpin({ yaw: 0, pitch: -300 }).pitch).toBe(-MAX_PITCH);
    expect(clampSpin({ yaw: 720, pitch: 10 }).yaw).toBe(720);
  });

  it("holds the flat reading at rest whatever it is handed", () => {
    const flat = (spin?: Spin): TerrainView => {
      const at: TerrainView = { zoom: 1, size, projection: "flat" };
      return spin === undefined ? at : { ...at, spin };
    };

    expect(cameraEye(flat({ yaw: 120, pitch: 5 }))).toStrictEqual(
      cameraEye(flat()),
    );
  });

  /**
   * The whole reason the projection is a module of its own: a mark's button is placed by the same function that
   * drew the mark. A turned camera is where that could quietly stop being true, so it is pinned against three.js's
   * own projection of the very same eye, as the resting camera already is.
   */
  it("agrees with three.js about where a mark lands, turned as well as at rest", () => {
    const point = planeToSphere(
      size.width * 0.62,
      size.height * 0.4,
      size,
      GLOBE_RADIUS + MARK_LIFT,
    );

    for (const spin of [
      restingSpin("globe"),
      { yaw: 55, pitch: 20 },
      { yaw: -140, pitch: -35 },
      { yaw: 200, pitch: MAX_PITCH },
    ]) {
      const view = globe(spin);
      const eye = cameraEye(view);
      const camera = new THREE.PerspectiveCamera(
        FOV,
        size.width / size.height,
        1,
        4000,
      );
      camera.position.set(eye.x, eye.y, eye.z);
      camera.lookAt(0, 0, 0);
      camera.updateMatrixWorld(true);

      const ndc = new THREE.Vector3(point.x, point.y, point.z).project(camera);
      const spot = projectToBox(point, view)!;

      expect(spot.left).toBeCloseTo((ndc.x + 1) / 2, 6);
      expect(spot.top).toBeCloseTo((1 - ndc.y) / 2, 6);
    }
  });

  // The far side is the thing dragging exists to reach, so what the overlay hides has to follow the camera.
  it("brings the hemisphere the reader turned towards into view, and hides the one they left", () => {
    const far = planeToSphere(
      size.width * 0.02,
      size.height / 2,
      size,
      GLOBE_RADIUS,
    );

    expect(facesCamera(far, globe())).toBe(false);
    expect(facesCamera(far, globe({ yaw: -170, pitch: 20 }))).toBe(true);
  });
});
