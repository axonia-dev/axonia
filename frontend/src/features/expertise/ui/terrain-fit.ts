/**
 * Where the eye has to stand for the whole of the terrain to be in frame, and the lens it stands behind.
 *
 * Apart from `terrain-camera.ts`, which points that eye and projects through it, because the two answer different
 * questions: this one is about what has to fit, that one about where a point of it lands in the box.
 */
import type {
  Projection,
  Vec3,
} from "@/features/expertise/ui/globe-projection";
import { restingSpin, type Spin } from "@/features/expertise/ui/terrain-aim";
import {
  type Lens,
  NEAR,
  projectFrom,
} from "@/features/expertise/ui/terrain-projection";
import { GLOBE_RADIUS } from "@/features/expertise/ui/terrain-surface";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/** The lens at rest. Zooming narrows it rather than moving the eye, so the fit is computed once for a whole zoom
 *  range - see `tanHalfOf` in `terrain-camera.ts`. */
export const FOV = 42;
export const FAR = 4000;

const HALF_FOV = ((FOV / 2) * Math.PI) / 180;
export const TAN_HALF_FOV = Math.tan(HALF_FOV);

/** How much of the box the terrain is asked to leave empty around itself, so it is framed rather than wedged in. */
const FRAMING = 1.06;

/** A rectangle of the plane, in layout pixels. */
export interface Extent {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

/**
 * How far a mark floats above the ground it stands on. The renderer draws the glyph layer at this lift and the
 * overlay projects its buttons from it, so a button and the mark it reaches are the same point in the world.
 */
export const MARK_LIFT = 4;

/** The eye's direction from the point it is aimed at: a unit vector, so that a distance means one. */
function eyeDirection(spin: Spin): Vec3 {
  const pitch = (spin.pitch * Math.PI) / 180;
  const yaw = (spin.yaw * Math.PI) / 180;
  const ring = Math.cos(pitch);
  return {
    x: ring * Math.sin(yaw),
    y: Math.sin(pitch),
    z: ring * Math.cos(yaw),
  };
}

/**
 * The narrower of the two half-angles the box offers, whichever that turns out to be. The box takes its shape from
 * the page - a share of the viewport's height, and whatever the shell's width leaves beside the readings panel - so
 * it is sometimes wider than it is tall and sometimes not, and a fit measured against one field alone runs the
 * terrain off the other two sides.
 */
function halfAngleOf(size: GraphSize): number {
  return Math.min(
    HALF_FOV,
    Math.atan((TAN_HALF_FOV * size.width) / size.height),
  );
}

/** Everything standing on the terrain: the ground is not displaced, so a mark at its lift is the whole of it. */
const REACH = MARK_LIFT;

export function eyeAt(distance: number, spin: Spin, target: Vec3): Vec3 {
  const eye = eyeDirection(spin);
  return {
    x: target.x + eye.x * distance,
    y: target.y + eye.y * distance,
    z: target.z + eye.z * distance,
  };
}

/** The four corners of what the flat reading has to hold, at the highest the ground under them could stand. */
function planeCorners(size: GraphSize, content?: Extent): Vec3[] {
  const box = content ?? {
    minX: 0,
    maxX: size.width,
    minY: 0,
    maxY: size.height,
  };
  return [box.minX, box.maxX].flatMap((x) =>
    [box.minY, box.maxY].map((y) => ({
      x: x - size.width / 2,
      y: REACH,
      z: y - size.height / 2,
    })),
  );
}

const ORIGIN: Vec3 = { x: 0, y: 0, z: 0 };

/** How far the furthest of these points strays from the middle of the box, in box fractions. */
function spread(points: Vec3[], size: GraphSize, distance: number): number {
  const lens: Lens = {
    eye: eyeAt(distance, restingSpin("flat"), ORIGIN),
    target: ORIGIN,
    size,
    tanHalf: TAN_HALF_FOV,
  };
  let worst = 0;
  for (const point of points) {
    const spot = projectFrom(point, lens);
    if (spot === null) return Number.POSITIVE_INFINITY;
    worst = Math.max(
      worst,
      Math.abs(spot.left - 0.5),
      Math.abs(spot.top - 0.5),
    );
  }
  return worst;
}

/**
 * The distance that just holds these points, found by measuring through the very projection the scene is drawn with
 * rather than by a half-extent derived on paper. A point's stray from the middle falls off as one over the distance,
 * so scaling the distance by how far the fit currently overshoots converges in a couple of passes.
 */
function fitTo(points: Vec3[], size: GraphSize): number {
  const target = 0.5 / FRAMING;
  let distance =
    2 * Math.max(...points.map((p) => Math.hypot(p.x, p.y, p.z))) + NEAR;
  for (let pass = 0; pass < 6; pass++) {
    distance *= spread(points, size, distance) / target;
  }
  return distance;
}

/**
 * The globe is a sphere about the point the camera looks at, so its fit needs no search: the eye stands where the
 * sphere's angular radius is the frame's.
 */
function globeDistance(size: GraphSize): number {
  return (FRAMING * (GLOBE_RADIUS + REACH)) / Math.sin(halfAngleOf(size));
}

/** What the fit has to hold, which is all of a view the distance depends on. */
export interface Framed {
  projection: Projection;
  size: GraphSize;
  content?: Extent;
}

/** Where the eye stands, which the zoom no longer moves: the whole terrain is held in frame at every zoom. */
export function distanceOf({ projection, size, content }: Framed): number {
  return projection === "flat"
    ? fitTo(planeCorners(size, content), size)
    : globeDistance(size);
}
