import type { Projection } from "@/features/expertise/ui/globe-projection";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * Where the camera is aimed and how much of the picture it holds: the three things a reader controls, and the bounds
 * that keep each of them somewhere the drawing still makes sense. The camera itself is built from these in
 * `terrain-camera.ts`, which is the only module that resolves them into an eye.
 */

/**
 * Where the eye stands on the sphere around the terrain, in degrees. Only the direction lives here; how far away the
 * eye is comes from the fit.
 */
export interface Spin {
  /** Around the terrain's own axis. Zero faces the middle of the plane, which is where the layout puts everything. */
  yaw: number;
  /** Above the terrain's plane. */
  pitch: number;
}

/** Where the camera is aimed, in the layout's own pixels, measured from the middle of the plane. */
export interface Pan {
  x: number;
  y: number;
}

/**
 * How high above the terrain's own plane the eye rests, in degrees.
 *
 * On the globe this decides which latitude faces the reader, and the plane's middle - where the layout is centred -
 * is the equator. High above it, the map opens on the water north of everything the community does, with its own
 * content pushed down towards the limb. Just enough of a tilt to read the relief as relief, and no more.
 *
 * The flat reading is the opposite case: a plane seen from low down is a wide, shallow thing that leaves the top and
 * bottom of its box empty, so it is seen from much higher up.
 */
const TILT: Record<Projection, number> = { globe: 15, flat: 68 };

/**
 * How near the pole a drag may take the eye. The camera's basis is built against world up, and at the pole itself
 * "right" stops being defined: the picture would flip through a singularity rather than keep turning.
 */
export const MAX_PITCH = 82;

/** Where the camera stands before anyone has dragged it, and where it returns whenever the reading changes. */
export function restingSpin(projection: Projection): Spin {
  return { yaw: 0, pitch: TILT[projection] };
}

export function clampSpin(spin: Spin): Spin {
  return {
    yaw: spin.yaw,
    pitch: Math.min(MAX_PITCH, Math.max(-MAX_PITCH, spin.pitch)),
  };
}

export const RESTING_PAN: Pan = { x: 0, y: 0 };

/** Zoomed all the way out the terrain sits in open space, which is as far back as there is anything to go back for. */
export const ZOOM_MIN = 0.6;
/**
 * Zoomed all the way in a single area fills the frame. High, because the whole point of the deep end is to separate
 * marks a community's busiest area piles on top of each other, and a ceiling of two never separated them.
 */
export const ZOOM_MAX = 8;
export const ZOOM_STEP = 1.15;

export function clampZoom(zoom: number): number {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, zoom));
}

/**
 * How far the aim may be carried: exactly as far as the zoom has pushed the picture out of the frame, and no
 * further. At rest the whole thing is already in view, so there is nowhere to go and a drag moves nothing; zoomed
 * in eightfold the reader can reach any corner and still cannot run the map off the edge of its own box.
 */
function panLimit(size: GraphSize, zoom: number): Pan {
  const share = Math.max(0, 1 - 1 / clampZoom(zoom)) / 2;
  return { x: size.width * share, y: size.height * share };
}

export function clampPan(pan: Pan, size: GraphSize, zoom: number): Pan {
  const limit = panLimit(size, zoom);
  return {
    x: Math.min(limit.x, Math.max(-limit.x, pan.x)),
    y: Math.min(limit.y, Math.max(-limit.y, pan.y)),
  };
}
