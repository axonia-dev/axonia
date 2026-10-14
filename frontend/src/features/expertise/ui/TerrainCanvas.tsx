import { useEffect, useRef } from "react";
import * as THREE from "three";

import type { MapPerson } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { paintNow, type Tinted } from "@/features/expertise/ui/echo-blend";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { faceSprites } from "@/features/expertise/ui/face-sprites";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { buildAtlas } from "@/features/expertise/ui/mark-atlas";
import { detailFor } from "@/features/expertise/ui/mark-lod";
import {
  type MarkLayer,
  markPoints,
} from "@/features/expertise/ui/mark-sprites";
import { haloLoops } from "@/features/expertise/ui/offer-halo";
import {
  cameraEye,
  fovOf,
  MARK_LIFT,
  targetOf,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import {
  DEEPEST,
  groundLayer,
  placeAt,
} from "@/features/expertise/ui/terrain-ground";
import {
  facePxAt,
  markPxAt,
  spriteScaleFor,
} from "@/features/expertise/ui/terrain-mark-size";
import { type Gpu, openGpu } from "@/features/expertise/ui/terrain-gpu";
import { groundColourAt } from "@/features/expertise/ui/terrain-shading";
import { standOnGround } from "@/features/expertise/ui/terrain-stencil";
import type { Surface } from "@/features/expertise/ui/terrain-surface";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
import { useTerrainEcho } from "@/features/expertise/ui/use-terrain-echo";
import { useThemePaint } from "@/features/expertise/ui/use-theme-paint";
import {
  hasWebGl2,
  rendererPixelRatio,
} from "@/features/expertise/ui/webgl-support";

const FACE_LIFT = 14;
const HALO_LIFT = 1;

interface Props {
  surface: Surface;
  view: TerrainView;
  marks: PlacedContribution[];
  topics: PlacedTopic[];
  people: ReadonlyMap<string, MapPerson>;
  faced: ReadonlySet<number>;
  /** What the drawing is about - the panel's areas, and the marks a term picked out of them - and how it gets there. */
  drawn: DrawnHighlight;
  /** The mark the strip below the drawing is describing, or null while nothing is chosen. */
  chosen: number | null;
}

/**
 * Takes a freshly built layer's colours into the live set, painted in the highlight that stands now, and gives back
 * what lets them go again when the layer is torn down.
 */
function holdLive(
  live: Set<Tinted>,
  built: readonly Tinted[],
  drawn: DrawnHighlight,
): () => void {
  for (const tint of built) {
    live.add(tint);
    paintNow(tint, drawn.highlight);
  }
  return () => {
    for (const tint of built) live.delete(tint);
  };
}

export function TerrainCanvas({
  surface,
  view,
  marks,
  topics,
  people,
  faced,
  drawn,
  chosen,
}: Readonly<Props>) {
  const hostRef = useRef<HTMLDivElement>(null);
  const gpuRef = useRef<Gpu | null>(null);
  const facesRef = useRef<THREE.Sprite[]>([]);
  // The scale the camera last gave the faces. A face layer rebuilt for anything else - a theme, a reading - starts
  // at three.js's default of one, which unattenuated draws a face as big as the globe, and the camera has not moved.
  const faceScaleRef = useRef(1);
  const resizeGlyphsRef = useRef<MarkLayer["resize"]>(() => {});
  // The highlight as it stands, read by a layer built after it moved, so a rebuilt layer starts in today's colours.
  const drawnRef = useRef(drawn);
  // Every colour the layers on screen hold, so a highlight repaints them in place rather than rebuilding them.
  const tintsRef = useRef(new Set<Tinted>());
  const supported = hasWebGl2();
  const painted = useThemePaint();
  const { size } = view;
  const markPx = markPxAt(view);
  // The glyph set, which changes once across the whole zoom range, rather than the size, which changes at every
  // click of the wheel: keying the layer on the size would rebuild it a dozen times on the way in for nothing.
  const detail = detailFor(markPx);

  // The renderer, taken once per box: see `openGpu` for why a context is never taken per rebuild.
  useEffect(() => {
    const host = hostRef.current;
    if (host === null || !supported) return undefined;

    const { gpu, close } = openGpu(host, size);
    gpuRef.current = gpu;
    return () => {
      gpuRef.current = null;
      close();
    };
  }, [size, supported]);

  /**
   * What the picture is cleared to.
   *
   * A map has no edge to see past, so the flat reading clears to its own deepest water: fitted to the land rather
   * than to the whole plane, the plane's far edge otherwise shows as a hard line with the page behind it. The globe
   * clears to nothing and keeps the page - it is a planet, and what is not the planet is not water.
   */
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return;

    const deep = groundColourAt(DEEPEST, groundPalette());
    gpu.renderer.setClearColor(
      new THREE.Color().setRGB(deep.r, deep.g, deep.b, THREE.SRGBColorSpace),
      surface.projection === "flat" ? 1 : 0,
    );
    gpu.request();
  }, [surface.projection, size, supported, painted]);

  // Before the layer effects, so a layer rebuilt in the same commit as a highlight moved starts in the new one.
  useEffect(() => {
    drawnRef.current = drawn;
  }, [drawn]);

  /**
   * The ground and what stands on it other than the contributions themselves.
   *
   * Where the camera stands is deliberately absent: a drag moves it every pointer event and a zoom every wheel
   * click, and rebuilding a heightfield of tens of thousands of shaded vertices at that rate is not a drag, it is a
   * slideshow. So is the mark a reader chose, which is why the glyphs are built by an effect of their own below:
   * choosing one must not cost a heightfield. Nor is the highlight: it only repaints what is already built.
   */
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const ground = groundLayer(surface, topics, marks);
    const faces = faceSprites({
      marks,
      faced,
      people,
      place: placeAt(surface, FACE_LIFT),
      repaint: gpu.request,
    });
    const scale = faceScaleRef.current;
    for (const sprite of faces.sprites) sprite.scale.set(scale, scale, 1);
    const halos = haloLoops(topics, placeAt(surface, HALO_LIFT));
    standOnGround([...faces.sprites, ...halos.loops]);

    const objects = [...ground.meshes, ...faces.sprites, ...halos.loops];
    for (const object of objects) gpu.scene.add(object);
    facesRef.current = faces.sprites;
    const release = holdLive(
      tintsRef.current,
      [ground.tint, faces.tint],
      drawnRef.current,
    );
    gpu.request();

    return () => {
      for (const object of objects) object.removeFromParent();
      facesRef.current = [];
      release();
      ground.dispose();
      faces.dispose();
      halos.dispose();
    };
  }, [surface, marks, topics, people, faced, supported, painted]);

  /** The contributions themselves, and the one of them the strip below is describing. */
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const atlas = new THREE.CanvasTexture(buildAtlas());
    const glyphs = markPoints(marks, placeAt(surface, MARK_LIFT), {
      atlas,
      detail,
      markPx,
      pixelRatio: rendererPixelRatio(),
      ...(chosen === null ? {} : { chosen }),
    });

    const objects = [...glyphs.halos, ...glyphs.points];
    standOnGround(objects);
    for (const object of objects) gpu.scene.add(object);
    resizeGlyphsRef.current = glyphs.resize;
    const release = holdLive(tintsRef.current, glyphs.tints, drawnRef.current);
    gpu.request();

    return () => {
      for (const object of objects) object.removeFromParent();
      resizeGlyphsRef.current = () => {};
      release();
      glyphs.dispose();
      atlas.dispose();
    };
    // `markPx` is read above and deliberately absent: it is the size the layer starts at, and the effect below
    // keeps it current without rebuilding anything.
    // oxlint-disable-next-line react/exhaustive-deps
  }, [surface, marks, detail, chosen, supported, painted]);

  // A highlight moved: what is on screen is carried to it in place, and drawn frame by frame while it moves.
  useTerrainEcho({
    gpuRef,
    tintsRef,
    layer: { surface, marks, drawn, markPx, supported, painted },
  });

  // Where the camera stands and how wide its lens is, moved in place rather than rebuilt, and drawn in the next frame.
  // This runs after the two effects above on mount, and before any frame they asked for, so nothing is ever drawn
  // from the origin or at a placeholder size.
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return;

    const eye = cameraEye(view);
    const at = targetOf(view);
    gpu.camera.position.set(eye.x, eye.y, eye.z);
    gpu.camera.lookAt(at.x, at.y, at.z);
    gpu.camera.fov = fovOf(view);
    gpu.camera.updateProjectionMatrix();

    // A mark and a face are sized in pixels, so how big they are drawn is a fact about the camera: set here, on
    // the objects themselves, rather than by rebuilding a layer of five thousand of them on every wheel click.
    resizeGlyphsRef.current(markPx);
    const scale = spriteScaleFor(facePxAt(view), view);
    faceScaleRef.current = scale;
    for (const sprite of facesRef.current) sprite.scale.set(scale, scale, 1);
    gpu.request();
  }, [view, markPx]);

  if (!supported) {
    return (
      <p role="status" className="text-muted-foreground p-6 text-sm">
        The map needs a browser with WebGL. Every topic is listed beside it.
      </p>
    );
  }

  return (
    <div
      ref={hostRef}
      aria-hidden="true"
      className="bg-surface-sunken block"
      style={{ width: size.width, height: size.height }}
    />
  );
}
