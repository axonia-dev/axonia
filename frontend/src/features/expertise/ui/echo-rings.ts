import * as THREE from "three";

import type { Ring } from "@/features/expertise/ui/echo-timeline";
import { accentHexOf } from "@/features/expertise/ui/group-accents";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import type { Place } from "@/features/expertise/ui/mark-sprites";
import { cast, mix, toRgb } from "@/features/expertise/ui/terrain-shading";

const SEGMENTS = 96;

/** More drops than this is a term matching most of the map, where one more ring says nothing the others have not. */
export const MAX_DROPS = 24;

export interface RingLayer {
  objects: THREE.LineLoop[];
  show: (rings: readonly Ring[]) => void;
  dispose: () => void;
}

/**
 * An area's accent carried half way to the summit, which is the right direction in both themes: it bleaches towards
 * white on the dark page and deepens on the light one, so a ring reads over its own area's ground.
 */
export function echoInk(slot: number): THREE.Color {
  const { r, g, b } = toRgb(
    mix(cast(accentHexOf(slot)), cast(groundPalette().summit), 0.5),
  );
  return new THREE.Color().setRGB(r, g, b, THREE.SRGBColorSpace);
}

function loopOf(): THREE.LineLoop<
  THREE.BufferGeometry,
  THREE.LineBasicMaterial
> {
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute(
    "position",
    new THREE.BufferAttribute(new Float32Array(SEGMENTS * 3), 3),
  );
  const material = new THREE.LineBasicMaterial({
    transparent: true,
    depthWrite: false,
  });
  const loop = new THREE.LineLoop(geometry, material);
  loop.visible = false;
  // A ring is redrawn somewhere new every frame, so the bounds three.js computed once would cull it wrongly.
  loop.frustumCulled = false;
  return loop;
}

/** A fixed pool of rings, rewritten in place every frame: an echo is seconds of frames, and none of them allocates. */
export function ringLayer({
  place,
  capacity,
}: Readonly<{ place: Place; capacity: number }>): RingLayer {
  const loops = Array.from({ length: capacity }, loopOf);
  const inks = new Map<number, THREE.Color>();
  const inkOf = (slot: number) => {
    const known = inks.get(slot);
    if (known !== undefined) return known;
    const ink = echoInk(slot);
    inks.set(slot, ink);
    return ink;
  };

  const draw = (loop: (typeof loops)[number], ring: Ring) => {
    const position = loop.geometry.getAttribute("position");
    for (let step = 0; step < SEGMENTS; step++) {
      const angle = (step / SEGMENTS) * Math.PI * 2;
      const at = place(
        ring.x + Math.cos(angle) * ring.radius,
        ring.y + Math.sin(angle) * ring.radius,
      );
      position.setXYZ(step, at.x, at.y, at.z);
    }
    position.needsUpdate = true;
    loop.material.opacity = ring.alpha;
    loop.material.color.copy(inkOf(ring.slot));
    loop.visible = true;
  };

  return {
    objects: loops,
    show: (rings) => {
      loops.forEach((loop, index) => {
        const ring = rings[index];
        if (ring === undefined) loop.visible = false;
        else draw(loop, ring);
      });
    },
    dispose: () => {
      for (const loop of loops) {
        loop.geometry.dispose();
        loop.material.dispose();
      }
    },
  };
}
