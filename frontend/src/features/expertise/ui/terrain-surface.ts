import type {
  Projection,
  Vec3,
} from "@/features/expertise/ui/globe-projection";
import { planeToSphere } from "@/features/expertise/ui/globe-projection";
import type { Heightfield } from "@/features/expertise/ui/terrain-heightfield";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

export const GLOBE_RADIUS = 160;

export interface Surface {
  field: Heightfield;
  size: GraphSize;
  projection: Projection;
}

/**
 * Where a point of the layout sits in the world. The renderer and the overlay both read the scene through this one
 * function, so a mark and the button that reaches it can never disagree about where it is.
 *
 * The ground is not displaced by its own height. It was, and it made the globe a stack of mesas: the heightfield
 * saturates, so land is a plateau with a cliff at the coast, and a cliff a tenth of the radius high casts a shadow
 * that reads as a mountain range nobody asked for and hangs off the limb as a wart. Height is carried entirely by
 * colour, which is what `terrain-shading.ts` is for and what the planet is meant to look like: a round world with
 * coastlines, not a relief model.
 */
export function surfacePoint(
  surface: Surface,
  x: number,
  y: number,
  lift: number,
): Vec3 {
  const { size, projection } = surface;

  if (projection === "flat") {
    return { x: x - size.width / 2, y: lift, z: y - size.height / 2 };
  }
  return planeToSphere(x, y, size, GLOBE_RADIUS + lift);
}
