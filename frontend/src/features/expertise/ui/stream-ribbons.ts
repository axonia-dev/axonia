import * as THREE from "three";

import type { Tinted } from "@/features/expertise/ui/echo-blend";
import {
  isLit,
  type MapHighlight,
} from "@/features/expertise/ui/map-highlight";
import { type Stacked, xOf } from "@/features/expertise/ui/stream-layout";
import {
  type BandShades,
  bandShades,
} from "@/features/expertise/ui/stream-paint";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

export interface RibbonLayer {
  objects: THREE.Object3D[];
  /** One RGBA tint per band and per edge, which a highlight repaints in place. */
  tints: Tinted[];
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

/**
 * A band's every vertex as one tint. Its colour and its opacity are both per vertex, which three.js reads from a
 * four component colour attribute, so an echo can carry the part of a band it has reached and leave the rest.
 */
function bandTint(
  geometry: THREE.BufferGeometry,
  tag: string,
  shades: BandShades,
): Tinted {
  const position = geometry.getAttribute("position");
  const xy = new Float32Array(position.count * 2);
  for (let vertex = 0; vertex < position.count; vertex++) {
    xy.set([position.getX(vertex), position.getY(vertex)], vertex * 2);
  }
  const colour = new THREE.BufferAttribute(
    new Float32Array(position.count * 4),
    4,
  );
  geometry.setAttribute("color", colour);

  return {
    xy,
    stride: 4,
    values: colour.array as Float32Array,
    targetFor: (highlight: MapHighlight) => {
      const shade = isLit(highlight.tags, [tag]) ? shades.lit : shades.back;
      const target = new Float32Array(position.count * 4);
      for (let vertex = 0; vertex < position.count; vertex++) {
        target.set(shade, vertex * 4);
      }
      return target;
    },
    commit: () => {
      colour.needsUpdate = true;
    },
  };
}

/**
 * Every band and its edge, built once per layout, size and theme. What a highlight changes is only their paint, so
 * they are built white and opaque and every colour and opacity lives in the vertices, where a tint can reach it.
 */
export function ribbons(stacked: Stacked[], size: GraphSize): RibbonLayer {
  const objects: THREE.Object3D[] = [];
  const tints: Tinted[] = [];
  const spent: { dispose: () => void }[] = [];

  for (const band of stacked) {
    const shades = bandShades(band);
    const geometry = ribbonGeometry(band, size);
    const material = new THREE.MeshBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 1,
      side: THREE.DoubleSide,
    });
    const edge = edgeGeometry(band, size);
    const edgeMaterial = new THREE.LineBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 1,
    });

    tints.push(bandTint(geometry, band.tag, shades.fill));
    tints.push(bandTint(edge, band.tag, shades.edge));
    objects.push(new THREE.Mesh(geometry, material));
    objects.push(new THREE.Line(edge, edgeMaterial));
    spent.push(geometry, material, edge, edgeMaterial);
  }

  return {
    objects,
    tints,
    dispose: () => {
      for (const item of spent) item.dispose();
    },
  };
}
