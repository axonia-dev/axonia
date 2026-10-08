import * as THREE from "three";

import { groundPalette } from "@/features/expertise/ui/ground-palette";
import type { Place } from "@/features/expertise/ui/mark-sprites";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

export interface HaloRing {
  tag: string;
  cx: number;
  cy: number;
  radius: number;
}

const BASE = 10;
const PER_OFFER = 4.5;
const MAX = 64;

/**
 * An offer is drawn and never measured. It is a dashed outline over the area's region rather than a mark
 * in it, because nothing was done at a time and there is nothing to open. An area carried only by declarations is an
 * outline on open water, which is exactly what it is.
 */
export function offerHalos(topics: PlacedTopic[]): HaloRing[] {
  return topics
    .filter((topic) => topic.declarations > 0)
    .map((topic) => ({
      tag: topic.tag,
      cx: topic.x,
      cy: topic.y,
      radius: Math.min(
        MAX,
        Math.max(topic.r + BASE, BASE + topic.declarations * PER_OFFER),
      ),
    }));
}

const SEGMENTS = 96;
const DASH = 6;
const GAP = 5;

export interface HaloLayer {
  loops: THREE.LineLoop[];
  dispose: () => void;
}

function ringPoints(halo: HaloRing, place: Place): THREE.Vector3[] {
  const points: THREE.Vector3[] = [];
  for (let step = 0; step < SEGMENTS; step++) {
    const angle = (step / SEGMENTS) * Math.PI * 2;
    points.push(
      place(
        halo.cx + Math.cos(angle) * halo.radius,
        halo.cy + Math.sin(angle) * halo.radius,
      ),
    );
  }
  return points;
}

export function haloLoops(topics: PlacedTopic[], place: Place): HaloLayer {
  const loops: THREE.LineLoop[] = [];
  const spent: { dispose: () => void }[] = [];

  for (const halo of offerHalos(topics)) {
    const geometry = new THREE.BufferGeometry();
    geometry.setFromPoints(ringPoints(halo, place));
    const material = new THREE.LineDashedMaterial({
      color: new THREE.Color(groundPalette().ring),
      dashSize: DASH,
      gapSize: GAP,
      transparent: true,
      opacity: 0.8,
    });
    const loop = new THREE.LineLoop(geometry, material);
    // LineDashedMaterial draws a solid line until this runs, and the dash is the whole point: it is what tells an
    // offer from practice everywhere else on the page.
    loop.computeLineDistances();

    loops.push(loop);
    spent.push(geometry, material);
  }

  return {
    loops,
    dispose: () => {
      for (const item of spent) item.dispose();
    },
  };
}
