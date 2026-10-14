import * as THREE from "three";

import { echoInk } from "@/features/expertise/ui/echo-rings";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import type { Place } from "@/features/expertise/ui/mark-sprites";

export interface TwinklePoint {
  x: number;
  y: number;
  reachedAtMs: number;
  /** The area the mark stands on, whose echo the twinkle is drawn in. */
  slot: number;
}

export interface TwinkleLayer {
  object: THREE.Points;
  at: (elapsedMs: number) => void;
  resize: (markPx: number) => void;
  dispose: () => void;
}

const VERTEX = `
attribute float aReached;
attribute vec3 aColour;
uniform float uElapsed;
uniform float uPx;
uniform float uDuration;
varying float vAlpha;
varying vec3 vColour;
void main() {
  vColour = aColour;
  float k = (uElapsed - aReached) / uDuration;
  vAlpha = (k < 0.0 || k > 1.0) ? 0.0 : 1.0 - k;
  gl_PointSize = vAlpha == 0.0 ? 0.0 : uPx * (1.0 + 2.5 * k);
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}`;

/** The colour is held in the renderer's working space, and a shader of our own writes it out unconverted unless told. */
const FRAGMENT = `
varying vec3 vColour;
varying float vAlpha;
void main() {
  float d = length(gl_PointCoord - 0.5);
  float a = smoothstep(0.5, 0.42, d) * smoothstep(0.30, 0.38, d) * vAlpha;
  if (a <= 0.0) discard;
  gl_FragColor = vec4(vColour, a);
  #include <colorspace_fragment>
}`;

/**
 * A small ring opening round every answer as the echo reaches it, in the ink of its own area's rings. Each point
 * carries its own moment, and the shader reads one clock, so a frame of the echo moves a single uniform rather than
 * thousands of attributes.
 */
export function twinkleLayer({
  points,
  place,
  markPx,
  pixelRatio,
}: Readonly<{
  points: readonly TwinklePoint[];
  place: Place;
  markPx: number;
  pixelRatio: number;
}>): TwinkleLayer {
  const positions = new Float32Array(points.length * 3);
  const reached = new Float32Array(points.length);
  const colours = new Float32Array(points.length * 3);
  const inks = new Map<number, THREE.Color>();
  points.forEach((point, index) => {
    const at = place(point.x, point.y);
    positions.set([at.x, at.y, at.z], index * 3);
    reached[index] = point.reachedAtMs;
    const ink = inks.get(point.slot) ?? echoInk(point.slot);
    inks.set(point.slot, ink);
    colours.set([ink.r, ink.g, ink.b], index * 3);
  });
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("aReached", new THREE.BufferAttribute(reached, 1));
  geometry.setAttribute("aColour", new THREE.BufferAttribute(colours, 3));

  const material = new THREE.ShaderMaterial({
    vertexShader: VERTEX,
    fragmentShader: FRAGMENT,
    transparent: true,
    depthWrite: false,
    uniforms: {
      uElapsed: { value: 0 },
      uPx: { value: markPx * pixelRatio },
      uDuration: { value: ECHO.twinkleMs },
    },
  });
  const object = new THREE.Points(geometry, material);
  object.frustumCulled = false;

  return {
    object,
    at: (elapsedMs) => {
      material.uniforms["uElapsed"]!.value = elapsedMs;
    },
    resize: (px) => {
      material.uniforms["uPx"]!.value = px * pixelRatio;
    },
    dispose: () => {
      geometry.dispose();
      material.dispose();
    },
  };
}
