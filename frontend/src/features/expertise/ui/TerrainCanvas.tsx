import { useEffect, useRef } from "react";
import * as THREE from "three";

import type { MapPerson } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  type DrawOnDemand,
  drawOnDemand,
} from "@/features/expertise/ui/draw-on-demand";
import { faceSprites } from "@/features/expertise/ui/face-sprites";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import {
  type MapHighlight,
  markLit,
} from "@/features/expertise/ui/map-highlight";
import { buildAtlas } from "@/features/expertise/ui/mark-atlas";
import { detailFor } from "@/features/expertise/ui/mark-lod";
import {
  type MarkLayer,
  markPoints,
} from "@/features/expertise/ui/mark-sprites";
import { haloLoops } from "@/features/expertise/ui/offer-halo";
import {
  cameraEye,
  FAR,
  FOV,
  fovOf,
  MARK_LIFT,
  NEAR,
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
import { groundColourAt } from "@/features/expertise/ui/terrain-shading";
import { standOnGround } from "@/features/expertise/ui/terrain-stencil";
import type { Surface } from "@/features/expertise/ui/terrain-surface";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
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
  /** What the drawing is about: the panel's areas, and the marks a term picked out of them. */
  highlight: MapHighlight;
  /** The mark the strip below the drawing is describing, or null while nothing is chosen. */
  chosen: number | null;
}

/** What the scene is drawn with, which outlives every rebuild of what is drawn in it. */
interface Gpu {
  renderer: THREE.WebGLRenderer;
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  /** Asks for the next frame: whatever changes the picture says so, and nothing is drawn otherwise. */
  request: DrawOnDemand["request"];
}

export function TerrainCanvas({
  surface,
  view,
  marks,
  topics,
  people,
  faced,
  highlight,
  chosen,
}: Readonly<Props>) {
  const hostRef = useRef<HTMLDivElement>(null);
  const gpuRef = useRef<Gpu | null>(null);
  const facesRef = useRef<THREE.Sprite[]>([]);
  // The scale the camera last gave the faces. A face layer rebuilt for anything else - a theme, a reading - starts
  // at three.js's default of one, which unattenuated draws a face as big as the globe, and the camera has not moved.
  const faceScaleRef = useRef(1);
  const resizeGlyphsRef = useRef<MarkLayer["resize"]>(() => {});
  const supported = hasWebGl2();
  const painted = useThemePaint();
  const { size } = view;
  const markPx = markPxAt(view);
  // The glyph set, which changes once across the whole zoom range, rather than the size, which changes at every
  // click of the wheel: keying the layer on the size would rebuild it a dozen times on the way in for nothing.
  const detail = detailFor(markPx);

  /**
   * The renderer, and nothing that depends on where the camera stands or on what the contributions are.
   *
   * A WebGL context is a scarce resource - a browser keeps about sixteen of them alive and starts losing the oldest
   * past that - so one is taken per box, not per rebuild, and it is handed straight back on the way out. Built on a
   * zoom, which is a dozen wheel clicks from one end of the range to the other, a renderer per rebuild spends the
   * whole budget in seconds and the context lost is the one on screen.
   */
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
    const camera = new THREE.PerspectiveCamera(
      FOV,
      size.width / size.height,
      NEAR,
      FAR,
    );

    // Intensities are in the renderer's own units, which are physical: since three r155 a light is no longer a
    // plain multiplier on the albedo, and it is short by a factor of PI. Read as multipliers, 0.86 and 0.4 drew
    // the globe at under half the colour the ramp asked for - a grey smudge where the flat reading, which is
    // unlit, was a map. Stated this way the two sum to the full colour at the brightest face, and the globe keeps
    // the gradient that tells a sphere from a disc.
    const FULL = Math.PI;
    scene.add(new THREE.AmbientLight(0xffffff, 0.88 * FULL));
    const key = new THREE.DirectionalLight(0xffffff, 0.12 * FULL);
    key.position.set(-0.4, 0.86, 0.32);
    scene.add(key);

    const drawing = drawOnDemand(renderer, host, () =>
      renderer.render(scene, camera),
    );
    gpuRef.current = { renderer, scene, camera, request: drawing.request };

    return () => {
      drawing.stop();
      gpuRef.current = null;
      // `dispose` frees what three.js allocated and leaves the context itself to the collector, which may be a
      // second or a minute away. Losing it on purpose gives it back now, which is the point of taking only one.
      renderer.forceContextLoss();
      renderer.dispose();
      renderer.domElement.remove();
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

  /**
   * The ground and what stands on it other than the contributions themselves.
   *
   * Where the camera stands is deliberately absent: a drag moves it every pointer event and a zoom every wheel
   * click, and rebuilding a heightfield of tens of thousands of shaded vertices at that rate is not a drag, it is a
   * slideshow. So is the mark a reader chose, which is why the glyphs are built by an effect of their own below:
   * choosing one must not cost a heightfield.
   */
  useEffect(() => {
    const gpu = gpuRef.current;
    if (gpu === null) return undefined;

    const ground = groundLayer(surface, topics, marks, highlight.tags);
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
    gpu.request();

    return () => {
      for (const object of objects) object.removeFromParent();
      facesRef.current = [];
      ground.dispose();
      faces.dispose();
      halos.dispose();
    };
  }, [
    surface,
    marks,
    topics,
    people,
    faced,
    highlight.tags,
    supported,
    painted,
  ]);

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
      lit: (mark) => markLit(highlight, mark),
      ...(chosen === null ? {} : { chosen }),
    });

    const objects = [...glyphs.halos, ...glyphs.points];
    standOnGround(objects);
    for (const object of objects) gpu.scene.add(object);
    resizeGlyphsRef.current = glyphs.resize;
    gpu.request();

    return () => {
      for (const object of objects) object.removeFromParent();
      resizeGlyphsRef.current = () => {};
      glyphs.dispose();
      atlas.dispose();
    };
    // `markPx` is read above and deliberately absent: it is the size the layer starts at, and the effect below
    // keeps it current without rebuilding anything.
    // oxlint-disable-next-line react/exhaustive-deps
  }, [surface, marks, detail, highlight, chosen, supported, painted]);

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
