import { useMemo } from "react";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import {
  streamLabels,
  terrainLabels,
} from "@/features/expertise/ui/map-labels";
import {
  type PlacedLabel,
  placeLabels,
} from "@/features/expertise/ui/map-labels-placement";
import type { MapReading } from "@/features/expertise/ui/MapReadingToggle";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { buildStream } from "@/features/expertise/ui/stream-bins";
import {
  binsFor,
  stack,
  type StreamCamera,
  streamPointOf,
  streamSpotOf,
} from "@/features/expertise/ui/stream-layout";
import { type Pan, type Spin } from "@/features/expertise/ui/terrain-aim";
import {
  globeDiscOf,
  terrainPointOf,
  terrainSpotOf,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { type Spot } from "@/features/expertise/ui/terrain-projection";
import type { Surface } from "@/features/expertise/ui/terrain-surface";
import { drawnExtent } from "@/features/expertise/ui/topic-graph-framing";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/** How squarely a point must face the camera before the globe will carry a name on it. */
const LABEL_FACING = 0.2;

export interface TerrainScene {
  surface: Surface;
  view: TerrainView;
  camera: StreamCamera;
  spotOf: (mark: PlacedContribution) => Spot | null;
  /** The names this reading has room for, at the camera it is being drawn at. */
  labels: PlacedLabel[];
}

/**
 * One surface and one camera, shared by the drawing and by everything that has to agree with it: the overlay places
 * a focusable button through the very function that drew a mark, a pointer picks through it too, and a name is put
 * over the ground it names through it as well, so none of the three can disagree about where anything is. Each
 * reading hands over its own.
 *
 * The surface and the camera are memoised apart, and that is not tidiness. The surface is what the renderer builds a
 * heightfield of tens of thousands of shaded vertices from, and it does not depend on where the camera stands.
 * Folded into one object it would be a new surface on every pointer event of a drag, and the scene would be rebuilt
 * under the reader's hand at the rate they were turning it. What names to draw is memoised the same way; where they
 * fit is not, because it is exactly what moving the camera changes.
 *
 * Null until the box has been measured: nothing may lay out against a size that is not yet known.
 */
export function useTerrainScene(
  at: Readonly<{
    marks: PlacedContribution[];
    topics: PlacedTopic[];
    groups: DrawnGroup[];
    size: GraphSize | null;
    projection: Projection;
    zoom: number;
    spin: Spin;
    pan: Pan;
    picture: MapReading;
  }>,
): TerrainScene | null {
  const { marks, topics, groups, size, projection, zoom, spin, pan, picture } =
    at;

  const surface = useMemo(
    () =>
      size === null
        ? null
        : { field: buildHeightfield(marks, size), size, projection },
    [marks, size, projection],
  );
  const view = useMemo(
    () =>
      size === null
        ? null
        : {
            zoom,
            size,
            projection,
            spin,
            pan,
            content: drawnExtent(topics, size),
          },
    [size, projection, zoom, spin, pan, topics],
  );
  const camera = useMemo(() => ({ zoom, pan }), [zoom, pan]);
  const terrainSpot = useMemo(
    () =>
      surface === null || view === null ? null : terrainSpotOf(surface, view),
    [surface, view],
  );
  const streamSpot = useMemo(
    () => (size === null ? null : streamSpotOf(marks, size, camera)),
    [marks, size, camera],
  );
  // A name placed right out on the limb is drawn along the edge of the globe and half of it lands on the page
  // behind: the marks there are three pixels wide and survive it, a word does not.
  const terrainPoint = useMemo(
    () =>
      surface === null || view === null
        ? null
        : terrainPointOf(surface, view, LABEL_FACING),
    [surface, view],
  );
  const streamPoint = useMemo(
    () => (size === null ? null : streamPointOf(size, camera)),
    [size, camera],
  );
  const names = useMemo(() => {
    if (size === null) return [];
    if (picture === "terrain") return terrainLabels(topics, groups, marks);
    const layout = buildStream(marks, binsFor(size.width));
    return streamLabels(stack(layout, size), groups, size);
  }, [picture, marks, topics, groups, size]);

  const spotOf = picture === "terrain" ? terrainSpot : streamSpot;
  const pointOf = picture === "terrain" ? terrainPoint : streamPoint;
  if (surface === null || view === null || size === null) return null;
  if (spotOf === null || pointOf === null) return null;

  return {
    surface,
    view,
    camera,
    spotOf,
    labels: placeLabels(
      names,
      pointOf,
      size,
      picture === "terrain" ? globeDiscOf(view) : null,
    ),
  };
}
