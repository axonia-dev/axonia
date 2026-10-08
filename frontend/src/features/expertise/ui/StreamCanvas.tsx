import { useEffect, useMemo, useRef } from "react";
import * as THREE from "three";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  type DrawOnDemand,
  drawOnDemand,
} from "@/features/expertise/ui/draw-on-demand";
import {
  isLit,
  type MapHighlight,
  markLit,
} from "@/features/expertise/ui/map-highlight";
import { buildAtlas } from "@/features/expertise/ui/mark-atlas";
import { detailFor } from "@/features/expertise/ui/mark-lod";
import { markPoints } from "@/features/expertise/ui/mark-sprites";
import { buildStream } from "@/features/expertise/ui/stream-bins";
import {
  binsFor,
  reseat,
  stack,
  type Stacked,
  type StreamCamera,
  streamFrustum,
  visibleSpan,
  xOf,
} from "@/features/expertise/ui/stream-layout";
import {
  BAND_OPACITY,
  bandColour,
  DIM_OPACITY,
  EDGE_OPACITY,
} from "@/features/expertise/ui/stream-paint";
import { StreamAxis } from "@/features/expertise/ui/StreamAxis";
import { markPxFor } from "@/features/expertise/ui/terrain-mark-size";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";
import { useThemePaint } from "@/features/expertise/ui/use-theme-paint";
import {
  hasWebGl2,
  rendererPixelRatio,
} from "@/features/expertise/ui/webgl-support";

interface Props {
  marks: PlacedContribution[];
  size: GraphSize;
  camera: StreamCamera;
  /** What the drawing is about: the panel's areas, and the marks a term picked out of them. */
  highlight: MapHighlight;
  /** The mark the strip below the drawing is describing, or null while nothing is chosen. */
  chosen: number | null;
}

const MARK_LIFT = 1;

interface Layer {
  objects: THREE.Object3D[];
  dispose: () => void;
}

function ribbonGeometry(band: Stacked, size: GraphSize): THREE.BufferGeometry {
  const bins = band.lower.length;
  const positions: number[] = [];
  const indices: number[] = [];

  for (let bin = 0; bin < bins; bin++) {
    const x = xOf(bin, bins, size);
    positions.push(x, band.lower[bin]!, 0, x, band.upper[bin]!, 0);
    if (bin === 0) continue;
    const previous = (bin - 1) * 2;
    indices.push(
      previous,
      previous + 1,
      previous + 2,
      previous + 2,
      previous + 1,
      previous + 3,
    );
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute(
    "position",
    new THREE.Float32BufferAttribute(positions, 3),
  );
  geometry.setIndex(indices);
  return geometry;
}

/** The band's own upper edge, drawn as a line. Filled alone, neighbouring bands of one colour read as a single
 *  shape; the edge is what says how many things a community was working on at once. */
function edgeGeometry(band: Stacked, size: GraphSize): THREE.BufferGeometry {
  const bins = band.upper.length;
  const positions: number[] = [];
  for (let bin = 0; bin < bins; bin++) {
    positions.push(xOf(bin, bins, size), band.upper[bin]!, 1);
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute(
    "position",
    new THREE.Float32BufferAttribute(positions, 3),
  );
  return geometry;
}

function ribbons(
  stacked: Stacked[],
  size: GraphSize,
  highlight: ReadonlySet<string> | null,
): Layer {
  const objects: THREE.Object3D[] = [];
  const spent: { dispose: () => void }[] = [];

  for (const band of stacked) {
    const lit = isLit(highlight, [band.tag]);
    const accent = bandColour(band, lit);
    const fill = lit ? BAND_OPACITY : DIM_OPACITY;
    const geometry = ribbonGeometry(band, size);
    const material = new THREE.MeshBasicMaterial({
      color: accent,
      transparent: true,
      opacity: fill,
      side: THREE.DoubleSide,
    });
    const edge = edgeGeometry(band, size);
    const edgeMaterial = new THREE.LineBasicMaterial({
      color: accent,
      transparent: true,
      opacity: lit ? EDGE_OPACITY : DIM_OPACITY,
    });

    objects.push(new THREE.Mesh(geometry, material));
    objects.push(new THREE.Line(edge, edgeMaterial));
    spent.push(geometry, material, edge, edgeMaterial);
  }

  return {
    objects,
    dispose: () => {
      for (const item of spent) item.dispose();
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
  highlight,
  chosen,
}: Readonly<Props>) {
  const hostRef = useRef<HTMLDivElement>(null);
  const cameraRef = useRef<THREE.OrthographicCamera | null>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const requestRef = useRef<DrawOnDemand["request"]>(() => {});
  const supported = hasWebGl2();
  const painted = useThemePaint();
  const markPx = markPxFor(size, camera.zoom);
  // The axis needs the span in the document while the scene needs the bands on the GPU, and both must be the same
  // reading of the same contributions; building it twice would also rebuild the whole scene on every render.
  const layout = useMemo(
    () => buildStream(marks, binsFor(size.width)),
    [marks, size.width],
  );

  // The renderer and its context, taken once per box. See the same split in `TerrainCanvas`: a context per rebuild
  // spends the browser's whole budget of them within a few wheel clicks.
  useEffect(() => {
    const host = hostRef.current;
    if (host === null || !supported) return undefined;

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(rendererPixelRatio());
    renderer.setSize(size.width, size.height);
    host.append(renderer.domElement);

    const scene = new THREE.Scene();
    // Orthographic, because a band's thickness IS the reading: perspective would make the same density mean two
    // different numbers depending on where along the axis it fell.
    const view = new THREE.OrthographicCamera(0, 0, 0, 0, 1, 1000);
    view.position.set(0, 0, 500);
    sceneRef.current = scene;
    cameraRef.current = view;
    const drawing = drawOnDemand(renderer, host, () =>
      renderer.render(scene, view),
    );
    requestRef.current = drawing.request;

    return () => {
      drawing.stop();
      sceneRef.current = null;
      cameraRef.current = null;
      requestRef.current = () => {};
      renderer.forceContextLoss();
      renderer.dispose();
      renderer.domElement.remove();
    };
  }, [size, supported]);

  useEffect(() => {
    const scene = sceneRef.current;
    if (scene === null) return undefined;

    const stacked = stack(layout, size);
    const bands = ribbons(stacked, size, highlight.tags);
    const atlas = new THREE.CanvasTexture(buildAtlas());
    const glyphs = markPoints(
      reseat(marks, layout, stacked, size),
      (x, y) => new THREE.Vector3(x, y, MARK_LIFT),
      {
        atlas,
        detail: detailFor(markPx),
        markPx,
        pixelRatio: rendererPixelRatio(),
        lit: (mark) => markLit(highlight, mark),
        ...(chosen === null ? {} : { chosen }),
      },
    );

    const objects = [...bands.objects, ...glyphs.halos, ...glyphs.points];
    for (const object of objects) scene.add(object);
    requestRef.current();

    return () => {
      for (const object of objects) object.removeFromParent();
      bands.dispose();
      glyphs.dispose();
      atlas.dispose();
    };
  }, [layout, marks, size, markPx, highlight, chosen, supported, painted]);

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
    requestRef.current();
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
