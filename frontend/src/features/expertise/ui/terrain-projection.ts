import type { Vec3 } from "@/features/expertise/ui/globe-projection";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/** Where something lands in the box the map is drawn in, as fractions of it. */
export interface Spot {
  left: number;
  top: number;
}

export function dot(a: Vec3, b: Vec3): number {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

export function normalized(v: Vec3): Vec3 {
  const length = Math.hypot(v.x, v.y, v.z) || 1;
  return { x: v.x / length, y: v.y / length, z: v.z / length };
}

interface Basis {
  right: Vec3;
  up: Vec3;
  back: Vec3;
}

/** The camera looks from the eye at the point it is aimed at, so its basis follows from those two alone. */
function basisFor(eye: Vec3, target: Vec3): Basis {
  const back = normalized({
    x: eye.x - target.x,
    y: eye.y - target.y,
    z: eye.z - target.z,
  });
  const right = normalized({ x: back.z, y: 0, z: -back.x });
  return {
    right,
    up: {
      x: back.y * right.z - back.z * right.y,
      y: back.z * right.x - back.x * right.z,
      z: back.x * right.y - back.y * right.x,
    },
    back,
  };
}

/** Nothing nearer than this is drawn, on the GPU or here. */
export const NEAR = 1;

/**
 * A perspective camera stated by where it stands rather than by a view, so the fit can try a distance before
 * adopting it and the overlay can project through the very camera the renderer was given.
 */
export interface Lens {
  eye: Vec3;
  target: Vec3;
  size: GraphSize;
  /** The tangent of half the vertical field of view: what the zoom actually narrows. */
  tanHalf: number;
}

/** Where a world point lands in the box, as fractions of it, or null when it sits behind the camera. */
export function projectFrom(point: Vec3, lens: Lens): Spot | null {
  const { eye, target, size, tanHalf } = lens;
  const { right, up, back } = basisFor(eye, target);
  const offset = { x: point.x - eye.x, y: point.y - eye.y, z: point.z - eye.z };

  const depth = -dot(offset, back);
  if (depth <= NEAR) return null;

  const aspect = size.width / size.height;
  return {
    left: 0.5 + dot(offset, right) / (2 * depth * tanHalf * aspect),
    top: 0.5 - dot(offset, up) / (2 * depth * tanHalf),
  };
}
