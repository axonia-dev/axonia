import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type {
  Projection,
  Vec3,
} from "@/features/expertise/ui/globe-projection";
import {
  clampPan,
  clampSpin,
  clampZoom,
  type Pan,
  restingSpin,
  type Spin,
} from "@/features/expertise/ui/terrain-aim";
import {
  distanceOf,
  type Extent,
  eyeAt,
  FAR,
  FOV,
  MARK_LIFT,
  TAN_HALF_FOV,
} from "@/features/expertise/ui/terrain-fit";
import {
  dot,
  NEAR,
  normalized,
  projectFrom,
  type Spot,
} from "@/features/expertise/ui/terrain-projection";
import {
  GLOBE_RADIUS,
  type Surface,
  surfacePoint,
} from "@/features/expertise/ui/terrain-surface";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/** The camera's world constants live with the fit that derives them, and are re-exported here because the scene is
 *  built through the camera. */
export { type Extent, FAR, FOV, MARK_LIFT, NEAR };

export interface TerrainView {
  zoom: number;
  size: GraphSize;
  projection: Projection;
  /**
   * The part of the plane anything is actually drawn on. Absent means the whole of it.
   *
   * The force layout settles a community into as tight an arrangement as its marks allow, so the plane around it is
   * open water - and on the flat reading, which sees the plane head on, framing the plane rather than the land opens
   * the map on a small island in an empty sea. The globe has no such choice: it wraps the whole plane whatever is
   * on it, and the empty part is the hemisphere turned away.
   */
  content?: Extent;
  /** Absent means at rest. See {@link spinOf}. */
  spin?: Spin;
  /** Absent means aimed at the middle of the plane. See {@link targetOf}. */
  pan?: Pan;
}

/**
 * The one place a view's spin is resolved, which is what keeps the drawing and the overlay from disagreeing: both
 * read the camera through {@link cameraEye} and {@link projectToBox}, and neither reads `spin` itself.
 *
 * A view with no spin is at rest, so every caller that does not turn the camera is spared saying so. The flat
 * reading is a map seen from above and is held at rest whatever it is handed: turning a plane the reader is looking
 * down at only rotates its north, and the globe is the reading that has a far side to reach.
 */
function spinOf(view: TerrainView): Spin {
  return view.projection === "flat" || view.spin === undefined
    ? restingSpin(view.projection)
    : clampSpin(view.spin);
}

/**
 * Where the camera is aimed. The globe is reached by turning it, so its aim stays on its centre and a pan it was
 * handed is ignored; the flat reading has no far side to turn to, so carrying the aim across the plane is the only
 * way a reader zoomed into one corner reaches another.
 */
export function targetOf(view: TerrainView): Vec3 {
  if (view.projection === "globe" || view.pan === undefined) {
    return { x: 0, y: 0, z: 0 };
  }
  const pan = clampPan(view.pan, view.size, view.zoom);
  return { x: pan.x, y: 0, z: pan.y };
}

/**
 * Zooming narrows the lens; it does not walk the eye in.
 *
 * Walking in is what a first reading of "zoom" suggests and it does not survive either projection. The globe is a
 * sphere of radius {@link GLOBE_RADIUS} and the eye reaches its surface a little past a factor of three, so past
 * that the camera is inside the planet. The plane fails less obviously and just as badly: an eye brought down to a
 * few hundred units above a terrain nine hundred wide is not reading a map any more, it is flying over one, and the
 * near corner is drawn at three times the scale of the far one. A narrower lens magnifies without either.
 */
export function tanHalfOf(view: TerrainView): number {
  return TAN_HALF_FOV / clampZoom(view.zoom);
}

/** What the perspective camera's own `fov` must be set to, in degrees, for {@link tanHalfOf} to be the lens it has. */
export function fovOf(view: TerrainView): number {
  return (2 * Math.atan(tanHalfOf(view)) * 180) / Math.PI;
}

export function cameraEye(view: TerrainView): Vec3 {
  return eyeAt(distanceOf(view), spinOf(view), targetOf(view));
}

/**
 * How much of the terrain one pixel of the box covers, at the depth the camera is aimed at. A drag that moves the
 * hand by so many pixels has to move the map by the same, or panning feels like it is pulling on something else.
 */
function worldPerPixel(view: TerrainView): number {
  return (2 * distanceOf(view) * tanHalfOf(view)) / view.size.height;
}

/**
 * How far a drag down the box carries the aim across the plane, per pixel. The plane is seen at a tilt, so a pixel
 * up the box covers more ground than a pixel across it, by exactly the foreshortening of that tilt.
 */
export function panPerPixel(view: TerrainView): Pan {
  const step = worldPerPixel(view);
  const pitch = (spinOf(view).pitch * Math.PI) / 180;
  return { x: step, y: step / Math.max(0.2, Math.sin(pitch)) };
}

/**
 * Where a world point lands in the box, as fractions of it, or null when it sits behind the camera. This is the same
 * perspective projection three.js applies to the scene, kept here so the overlay never has to guess at it a second
 * time: a button and the mark it reaches are placed by one function.
 */
export function projectToBox(point: Vec3, view: TerrainView): Spot | null {
  return projectFrom(point, {
    eye: cameraEye(view),
    target: targetOf(view),
    size: view.size,
    tanHalf: tanHalfOf(view),
  });
}

/**
 * How squarely a point on the globe faces the camera: 1 dead ahead, 0 on the limb, negative round the back. The
 * visible hemisphere is the one whose outward normal leans towards the eye, which is not the same as the half with a
 * positive z: the camera looks down at the terrain.
 */
export function facingOf(point: Vec3, view: TerrainView): number {
  const eye = cameraEye(view);
  const toEye = { x: eye.x - point.x, y: eye.y - point.y, z: eye.z - point.z };
  return dot(normalized(point), normalized(toEye));
}

/** True when a point on the globe faces the camera at all. */
export function facesCamera(point: Vec3, view: TerrainView): boolean {
  return facingOf(point, view) > 0;
}

/**
 * True when the sight line to a point meets the planet, so the ground is what the point is seen against. A mark stands
 * a little off the ground, and right at the limb that lift carries it past the planet's silhouette while it still
 * faces the camera: the drawing cuts it away there, so nothing may be placed on it either.
 */
function seenAgainstGround(point: Vec3, view: TerrainView): boolean {
  const eye = cameraEye(view);
  const sight = normalized({
    x: point.x - eye.x,
    y: point.y - eye.y,
    z: point.z - eye.z,
  });
  const along = dot(eye, sight);
  return dot(eye, eye) - along * along <= GLOBE_RADIUS * GLOBE_RADIUS;
}

function hiddenOnGlobe(
  point: Vec3,
  view: TerrainView,
  minFacing: number,
): boolean {
  return (
    facingOf(point, view) <= Math.max(0, minFacing) ||
    !seenAgainstGround(point, view)
  );
}

/** A circle in the box, in pixels. */
export interface Disc {
  x: number;
  y: number;
  r: number;
}

/**
 * The globe's outline in the box, or null for the flat reading, which has no edge of its own inside the box. The
 * camera aims at the planet's centre, so the outline is a circle round the middle of the box, as wide as the cone of
 * sight lines that graze the sphere.
 */
export function globeDiscOf(view: TerrainView): Disc | null {
  if (view.projection !== "globe") return null;
  const eye = cameraEye(view);
  const distance = Math.hypot(eye.x, eye.y, eye.z);
  const grazing =
    GLOBE_RADIUS / Math.sqrt(distance * distance - GLOBE_RADIUS * GLOBE_RADIUS);
  return {
    x: view.size.width / 2,
    y: view.size.height / 2,
    r: (grazing / (2 * tanHalfOf(view))) * view.size.height,
  };
}

function inBox(spot: Spot): boolean {
  return spot.left >= 0 && spot.left <= 1 && spot.top >= 0 && spot.top <= 1;
}

/**
 * Where a point of the layout sits on the terrain, read through the same surface and the same camera the renderer
 * draws it with, or null when the reader cannot see it: behind the camera, outside the box, on the hemisphere turned
 * away, or lifted out past the limb. Marks and names are placed through this one function, so a label cannot drift
 * off the ground it names.
 */
export function terrainPointOf(
  surface: Surface,
  view: TerrainView,
  minFacing = 0,
): (x: number, y: number) => Spot | null {
  return (x, y) => {
    const point = surfacePoint(surface, x, y, MARK_LIFT);
    if (view.projection === "globe" && hiddenOnGlobe(point, view, minFacing)) {
      return null;
    }

    const spot = projectToBox(point, view);
    return spot === null || !inBox(spot) ? null : spot;
  };
}

/**
 * Where a mark's button sits on the terrain. The overlay takes this callback and knows nothing of the surface or the
 * camera, which is what lets one overlay serve both readings.
 */
export function terrainSpotOf(
  surface: Surface,
  view: TerrainView,
): (mark: PlacedContribution) => Spot | null {
  const pointOf = terrainPointOf(surface, view);
  return (mark) => pointOf(mark.x, mark.y);
}
