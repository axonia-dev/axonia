import { useEffect, useMemo, useRef } from "react";
import * as THREE from "three";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { drawOnDemand } from "@/features/expertise/ui/draw-on-demand";
import { MAX_DROPS } from "@/features/expertise/ui/echo-drops";
import { ringLayer } from "@/features/expertise/ui/echo-rings";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { twinkleLayer } from "@/features/expertise/ui/echo-twinkles";
import { buildAtlas } from "@/features/expertise/ui/mark-atlas";
import { detailFor } from "@/features/expertise/ui/mark-lod";
import { markPoints } from "@/features/expertise/ui/mark-sprites";
import { buildStream } from "@/features/expertise/ui/stream-bins";
import {
  binsFor,
  reseat,
  stack,
  type StreamCamera,
  streamFrustum,
  visibleSpan,
} from "@/features/expertise/ui/stream-layout";
import { ribbons } from "@/features/expertise/ui/stream-ribbons";
import { StreamAxis } from "@/features/expertise/ui/StreamAxis";
import { markPxFor } from "@/features/expertise/ui/terrain-mark-size";
import {
  stampGround,
  standOnGround,
} from "@/features/expertise/ui/terrain-stencil";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";
import {
  type EchoGpu,
  type EchoGround,
  holdLive,
  liveTints,
  useMapEcho,
} from "@/features/expertise/ui/use-map-echo";
import { useThemePaint } from "@/features/expertise/ui/use-theme-paint";
import {
  hasWebGl2,
  rendererPixelRatio,
} from "@/features/expertise/ui/webgl-support";

interface Props {
  marks: PlacedContribution[];
  size: GraphSize;
  camera: StreamCamera;
  /** What the drawing is about - the panel's areas, and the marks a term picked out of them - and how it gets there. */
  drawn: DrawnHighlight;
  /** The mark the strip below the drawing is describing, or null while nothing is chosen. */
  chosen: number | null;
}

const MARK_LIFT = 1;
/** Over the bands and under their edges and every mark: a ring passes beneath what it is about. */
const RING_LIFT = MARK_LIFT - 0.5;
/** Over the marks a twinkle opens round, and under the one a reader chose. */
const TWINKLES_ORDER = 1.5;

/**
 * The stream's echo: the plane it is laid out on is centred on the camera, and a ring is drawn flat over it, but only
 * where the bands are. The background beyond them is no ground, and a ring crossing it would be about nothing.
 */
function streamGround(
  size: GraphSize,
  seated: readonly PlacedContribution[],
): EchoGround {
  return {
    bounds: {
      minX: -size.width / 2,
      maxX: size.width / 2,
      minY: -size.height / 2,
      maxY: size.height / 2,
    },
    marks: seated,
    rings: () => {
      const rings = ringLayer({
        place: (x, y) => new THREE.Vector3(x, y, RING_LIFT),
        capacity: ECHO.ringLagsMs.length * MAX_DROPS,
      });
      standOnGround(rings.objects);
      return rings;
    },
    twinkles: ({ points, markPx }) => {
      const made = twinkleLayer({
        points,
        place: (x, y) => new THREE.Vector3(x, y, MARK_LIFT),
        markPx,
        pixelRatio: rendererPixelRatio(),
      });
      made.object.renderOrder = TWINKLES_ORDER;
      return made;
    },
  };
}

/**
 * The second reading: the same contributions as the terrain, stacked as density bands with time along the horizontal
 * axis. Adjacency is lost here and survives only as colour, which is the trade this reading makes to show a practice
 * as a history rather than a state.
 */
export function StreamCanvas({
  marks,
  size,
  camera,
  drawn,
  chosen,
}: Readonly<Props>) {
  const hostRef = useRef<HTMLDivElement>(null);
  const cameraRef = useRef<THREE.OrthographicCamera | null>(null);
  const gpuRef = useRef<EchoGpu | null>(null);
  // The highlight as it stands, read by a layer built after it moved, so a rebuilt layer starts in today's colours.
  const drawnRef = useRef(drawn);
  // Every colour the layers on screen hold, so a highlight repaints them in place rather than rebuilding them.
  const tintsRef = useRef(liveTints());
  const supported = hasWebGl2();
  const painted = useThemePaint();
  const markPx = markPxFor(size, camera.zoom);
  // The axis needs the span in the document while the scene needs the bands on the GPU, and both must be the same
  // reading of the same contributions; building it twice would also rebuild the whole scene on every render.
  const layout = useMemo(
    () => buildStream(marks, binsFor(size.width)),
    [marks, size.width],
  );
  const stacked = useMemo(() => stack(layout, size), [layout, size]);
  const seated = useMemo(
    () => reseat(marks, layout, stacked, size),
    [marks, layout, stacked, size],
  );
  const ground = useMemo(() => streamGround(size, seated), [size, seated]);

  // The renderer and its context, taken once per box. See the same split in `TerrainCanvas`: a context per rebuild
  // spends the browser's whole budget of them within a few wheel clicks.
  useEffect(() => {
    const host = hostRef.current;
    if (host === null || !supported) return undefined;

    const renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
      stencil: true,
    });
    renderer.setPixelRatio(rendererPixelRatio());
    renderer.setSize(size.width, size.height);
    host.append(renderer.domElement);

    const scene = new THREE.Scene();
    // Orthographic, because a band's thickness IS the reading: perspective would make the same density mean two
    // different numbers depending on where along the axis it fell.
    const view = new THREE.OrthographicCamera(0, 0, 0, 0, 1, 1000);
    view.position.set(0, 0, 500);
    cameraRef.current = view;
    const drawing = drawOnDemand(renderer, host, () =>
      renderer.render(scene, view),
    );
    gpuRef.current = {
      scene,
      request: drawing.request,
      onScreen: drawing.onScreen,
    };

    return () => {
      drawing.stop();
      gpuRef.current = null;
      cameraRef.current = null;
      renderer.forceContextLoss();
      renderer.dispose();
      renderer.domElement.remove();
    };
  }, [size, supported]);

  // Before the layer effects, so a layer rebuilt in the same commit as a highlight moved starts in the new one.
  useEffect(() => {
    drawnRef.current = drawn;
  }, [drawn]);

  // The bands, built once per history, size and theme: a highlight only repaints them.
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const bands = ribbons(stacked, size);
    stampGround(bands.fills);
    for (const object of bands.objects) gpu.scene.add(object);
    const release = holdLive(tintsRef.current, bands.tints, drawnRef.current);
    gpu.request();

    return () => {
      for (const object of bands.objects) object.removeFromParent();
      release();
      bands.dispose();
    };
  }, [stacked, size, supported, painted]);

  /** The contributions themselves, seated on their bands, and the one of them the strip below is describing. */
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const atlas = new THREE.CanvasTexture(buildAtlas());
    const glyphs = markPoints(
      seated,
      (x, y) => new THREE.Vector3(x, y, MARK_LIFT),
      {
        atlas,
        detail: detailFor(markPx),
        markPx,
        pixelRatio: rendererPixelRatio(),
        ...(chosen === null ? {} : { chosen }),
      },
    );

    const objects = [...glyphs.halos, ...glyphs.points];
    for (const object of objects) gpu.scene.add(object);
    const release = holdLive(tintsRef.current, glyphs.tints, drawnRef.current);
    gpu.request();

    return () => {
      for (const object of objects) object.removeFromParent();
      release();
      glyphs.dispose();
      atlas.dispose();
    };
  }, [seated, markPx, chosen, supported, painted]);

  // A highlight moved: the bands and the marks are carried to it in place, and drawn frame by frame while they move.
  useMapEcho({
    gpuRef,
    tintsRef,
    layer: { ground, drawn, markPx, supported, painted },
  });

  // The frame the camera holds, moved in place: zooming and panning must not rebuild a history.
  useEffect(() => {
    const view = cameraRef.current;
    if (view === null) return;
    const { left, right, top, bottom } = streamFrustum(size, camera);
    view.left = left;
    view.right = right;
    view.top = top;
    view.bottom = bottom;
    view.updateProjectionMatrix();
    gpuRef.current?.request();
  }, [size, camera]);

  if (!supported) {
    return (
      <p role="status" className="text-muted-foreground p-6 text-sm">
        The map needs a browser with WebGL. Every topic is listed beside it.
      </p>
    );
  }

  return (
    <div
      className="relative"
      style={{ width: size.width, height: size.height }}
    >
      <div
        ref={hostRef}
        aria-hidden="true"
        className="bg-surface-sunken block"
        style={{ width: size.width, height: size.height }}
      />
      <StreamAxis {...visibleSpan(layout, size, camera)} />
      {marks.length === 0 && (
        <p className="text-muted-foreground pointer-events-none absolute inset-0 grid place-items-center text-sm">
          Nothing has been shared yet.
        </p>
      )}
    </div>
  );
}
