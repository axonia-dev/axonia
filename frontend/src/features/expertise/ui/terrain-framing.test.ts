import { describe, expect, it } from "vitest";

import type { Vec3 } from "@/features/expertise/ui/globe-projection";
import { planeToSphere } from "@/features/expertise/ui/globe-projection";
import { ZOOM_MAX, ZOOM_MIN } from "@/features/expertise/ui/terrain-aim";
import {
  cameraEye,
  facesCamera,
  MARK_LIFT,
  projectToBox,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import {
  GLOBE_RADIUS,
  surfacePoint,
} from "@/features/expertise/ui/terrain-surface";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/**
 * The camera has to hold the whole terrain at rest and may crop only once the reader has asked to come closer. The
 * box it holds it in is not always landscape: the map sits in a grid cell beside a 296px panel, so on a laptop it is
 * taller than it is wide, and a fit measured against the vertical field alone pushes the globe off both sides.
 */
describe("the framing", () => {
  const boxes = [
    { width: 800, height: 500 },
    { width: 397, height: 445 },
    { width: 500, height: 500 },
  ];
  const resting = [ZOOM_MIN, 0.7, 1];

  /** Sampled off the globe itself rather than off the radius the fit is derived from, so the two are independent. */
  function globeSkin(box: GraphSize): Vec3[] {
    const points: Vec3[] = [];
    for (let lat = -12; lat <= 12; lat++) {
      for (let lon = 0; lon < 24; lon++) {
        points.push(
          planeToSphere(
            (lon / 24) * box.width,
            ((lat + 12) / 24) * box.height,
            box,
            GLOBE_RADIUS + MARK_LIFT,
          ),
        );
      }
    }
    return points;
  }

  function planeCorners(box: GraphSize): Vec3[] {
    return [-1, 1].flatMap((sx) =>
      [-1, 1].map((sz) => ({
        x: (sx * box.width) / 2,
        y: MARK_LIFT,
        z: (sz * box.height) / 2,
      })),
    );
  }

  function seen(points: Vec3[], v: TerrainView) {
    return points
      .map((point) => projectToBox(point, v))
      .filter((spot): spot is NonNullable<typeof spot> => spot !== null);
  }

  it.each(boxes)("holds the whole globe in a $width x $height box", (box) => {
    for (const zoom of resting) {
      const v = { zoom, size: box, projection: "globe" as const };
      const facing = globeSkin(box).filter((point) => facesCamera(point, v));

      expect(facing.length).toBeGreaterThan(100);
      for (const spot of seen(facing, v)) {
        expect(spot.left).toBeGreaterThanOrEqual(0);
        expect(spot.left).toBeLessThanOrEqual(1);
        expect(spot.top).toBeGreaterThanOrEqual(0);
        expect(spot.top).toBeLessThanOrEqual(1);
      }
    }
  });

  it.each(boxes)(
    "holds the whole flat plane in a $width x $height box",
    (box) => {
      for (const zoom of resting) {
        const v = { zoom, size: box, projection: "flat" as const };
        const spots = seen(planeCorners(box), v);

        expect(spots).toHaveLength(4);
        for (const spot of spots) {
          expect(spot.left).toBeGreaterThanOrEqual(0);
          expect(spot.left).toBeLessThanOrEqual(1);
          expect(spot.top).toBeGreaterThanOrEqual(0);
          expect(spot.top).toBeLessThanOrEqual(1);
        }
      }
    },
  );

  it.each(boxes)(
    "fills a $width x $height box rather than floating in it",
    (box) => {
      const v = { zoom: 1, size: box, projection: "globe" as const };
      const spots = seen(
        globeSkin(box).filter((point) => facesCamera(point, v)),
        v,
      );
      const across =
        Math.max(...spots.map((s) => s.left)) -
        Math.min(...spots.map((s) => s.left));
      const down =
        Math.max(...spots.map((s) => s.top)) -
        Math.min(...spots.map((s) => s.top));

      expect(Math.max(across, down)).toBeGreaterThan(0.7);
    },
  );

  /**
   * Zoom narrows the lens; it does not walk the eye in. Dollied, the eye reached the globe's own surface a little
   * past a factor of three, so the deep end of the range was spent inside the planet.
   */
  it.each(boxes)(
    "keeps the eye clear of the globe in a $width x $height box",
    (box) => {
      for (const zoom of [...resting, 3, ZOOM_MAX]) {
        const eye = cameraEye({ zoom, size: box, projection: "globe" });

        expect(Math.hypot(eye.x, eye.y, eye.z)).toBeGreaterThan(
          GLOBE_RADIUS + MARK_LIFT,
        );
      }
    },
  );

  it("crops once the reader has asked to come closer", () => {
    const box = boxes[1]!;
    const v = { zoom: ZOOM_MAX, size: box, projection: "globe" as const };
    const facing = globeSkin(box).filter((point) => facesCamera(point, v));

    const inside = seen(facing, v).filter(
      (spot) =>
        spot.left >= 0 && spot.left <= 1 && spot.top >= 0 && spot.top <= 1,
    );

    expect(inside.length).toBeLessThan(facing.length / 2);
  });
});

/**
 * The force layout settles a community into as tight an arrangement as its marks allow, so a map framed on the
 * plane rather than on what is drawn on it opens on a small island in an empty sea.
 */
describe("framing the flat reading on the land", () => {
  const box: GraphSize = { width: 800, height: 500 };
  const land = { minX: 300, maxX: 500, minY: 200, maxY: 300 };

  /** How much of the box the same patch of ground takes up, measured through the projection it is drawn with. */
  function acrossTheBox(view: TerrainView): number {
    const spot = (x: number, y: number) =>
      projectToBox(
        surfacePoint(
          { field: FIELD, size: box, projection: "flat" },
          x,
          y,
          MARK_LIFT,
        ),
        view,
      )!;
    return Math.abs(
      spot(land.maxX, land.minY).left - spot(land.minX, land.minY).left,
    );
  }

  const FIELD = { cols: 1, rows: 1, cell: 1, values: new Float32Array(1) };

  it("draws the land larger than a fit on the whole plane would", () => {
    const whole = acrossTheBox({ zoom: 1, size: box, projection: "flat" });
    const fitted = acrossTheBox({
      zoom: 1,
      size: box,
      projection: "flat",
      content: land,
    });

    expect(fitted).toBeGreaterThan(whole * 1.5);
  });

  it("still holds the whole of it in frame", () => {
    const view: TerrainView = {
      zoom: 1,
      size: box,
      projection: "flat",
      content: land,
    };
    const corners = [land.minX, land.maxX].flatMap((x) =>
      [land.minY, land.maxY].map((y) =>
        projectToBox(
          surfacePoint(
            { field: FIELD, size: box, projection: "flat" },
            x,
            y,
            MARK_LIFT,
          ),
          view,
        )!,
      ),
    );

    for (const corner of corners) {
      expect(corner.left).toBeGreaterThanOrEqual(0);
      expect(corner.left).toBeLessThanOrEqual(1);
      expect(corner.top).toBeGreaterThanOrEqual(0);
      expect(corner.top).toBeLessThanOrEqual(1);
    }
  });

  /** The globe wraps the whole plane whatever is on it, and the empty part is the hemisphere turned away. */
  it("leaves the globe's fit alone", () => {
    const at = (content?: TerrainView["content"]) =>
      cameraEye({
        zoom: 1,
        size: box,
        projection: "globe",
        ...(content === undefined ? {} : { content }),
      });

    expect(at(land)).toStrictEqual(at());
  });
});
