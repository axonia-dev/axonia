import * as THREE from "three";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import {
  LATITUDE_SPAN,
  type Projection,
} from "@/features/expertise/ui/globe-projection";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { accentHexOf } from "@/features/expertise/ui/group-accents";
import { isLit } from "@/features/expertise/ui/map-highlight";
import type { Place } from "@/features/expertise/ui/mark-sprites";
import {
  groundColourAt,
  type GroundPalette,
  nearestRegion,
  type RegionSeed,
  regionSeeds,
} from "@/features/expertise/ui/terrain-shading";
import { stampGround } from "@/features/expertise/ui/terrain-stencil";
import {
  GLOBE_RADIUS,
  type Surface,
  surfacePoint,
} from "@/features/expertise/ui/terrain-surface";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

/**
 * How far the cap sits under the lowest the faceted ground sags. The cap is the planet's outline wherever the camera
 * looks over a pole, so every unit it sinks is a dent in the limb: two units drew a flat top four pixels deep.
 */
const CAP_CLEARANCE = 0.25;

/** Fine enough that the cap's own facets do not show as corners on the limb. */
const CAP_SEGMENTS = { around: 128, down: 64 } as const;

/** The ground's flat quads sag below the sphere they approximate, most at a quad's centre and most of all on the
 *  equator, where a step of longitude is widest. */
function capRadius({ field, size }: Surface): number {
  const halfLongitude = (Math.PI * field.cell) / size.width;
  const halfLatitude =
    (Math.PI * LATITUDE_SPAN * field.cell) / (2 * size.height);
  const sag =
    GLOBE_RADIUS * (1 - Math.cos(halfLongitude) * Math.cos(halfLatitude));
  return GLOBE_RADIUS - sag - CAP_CLEARANCE;
}

/** Open water where nothing at all has happened, which is what the cap under the globe is painted. */
export const DEEPEST = { height: 0, accent: null, nearness: 0 } as const;

/**
 * The ground is painted per vertex rather than given one colour, because the relief IS the reading: under a single
 * flat colour a heightfield lit by one raking light is a blob whose hills read as noise.
 *
 * The flat reading is then drawn unlit. Its every face points at the reader, so a light adds nothing to it but its
 * own intensity - over one, which clips the ramp towards white and turns a blue sea grey. The globe keeps the lit
 * material: there the light is what makes a sphere read as a sphere rather than as a disc.
 */
function groundMaterial(projection: Projection): THREE.Material {
  return projection === "flat"
    ? new THREE.MeshBasicMaterial({ vertexColors: true })
    : new THREE.MeshStandardMaterial({
        vertexColors: true,
        roughness: 0.95,
        metalness: 0,
      });
}

/** Where a thing standing on the surface sits in the scene. Everything the renderer draws is placed through this,
 *  so the ground, the marks, the faces and the halos are all read off the one surface. */
export function placeAt(surface: Surface, lift: number): Place {
  return (x, y) => {
    const at = surfacePoint(surface, x, y, lift);
    return new THREE.Vector3(at.x, at.y, at.z);
  };
}

/** One instance for the tens of thousands of conversions a heightfield asks for, rather than one per vertex. */
const scratch = new THREE.Color();

/**
 * The shading module answers in sRGB, which is the space the accent tokens and the mark atlas are written in, so the
 * conversion into the renderer's working space is named here rather than left to three.js's default.
 */
function pushColour(into: number[], r: number, g: number, b: number): void {
  scratch.setRGB(r, g, b, THREE.SRGBColorSpace);
  into.push(scratch.r, scratch.g, scratch.b);
}

/** Resolved once per neighbourhood, not once per vertex: `accentHexOf` reads the live cascade, and a heightfield
 *  asks for a colour tens of thousands of times. */
function accentsFor(seeds: RegionSeed[]): Map<number, string> {
  return new Map(
    [...new Set(seeds.map((seed) => seed.slot))].map((index) => [
      index,
      accentHexOf(index),
    ]),
  );
}

/**
 * Open water and land no area claims are never dimmed. They carry no neighbourhood's colour to turn down, so the only
 * thing dimming them would do is darken the map around a reading rather than pick anything out of it.
 */
function vertexColours(
  surface: Surface,
  seeds: RegionSeed[],
  highlight: ReadonlySet<string> | null,
  palette: GroundPalette,
): number[] {
  const { field } = surface;
  const accents = accentsFor(seeds);
  const colours: number[] = [];

  for (let row = 0; row < field.rows; row++) {
    for (let col = 0; col < field.cols; col++) {
      const height = field.values[row * field.cols + col]!;
      const region = nearestRegion(seeds, col * field.cell, row * field.cell);
      const { r, g, b } = groundColourAt(
        {
          height,
          accent: region === null ? null : (accents.get(region.slot) ?? null),
          nearness: region?.nearness ?? 0,
          dim: region !== null && !isLit(highlight, [region.tag]),
        },
        palette,
      );
      pushColour(colours, r, g, b);
    }
  }
  return colours;
}

function heightfieldMesh(
  surface: Surface,
  seeds: RegionSeed[],
  highlight: ReadonlySet<string> | null,
  palette: GroundPalette,
): THREE.Mesh {
  const { field } = surface;
  const positions: number[] = [];
  const indices: number[] = [];
  const ground = placeAt(surface, 0);

  // One vertex per grid point, SHARED by the four cells meeting there. Lifting per cell tears the mesh into ribbons:
  // neighbouring cells stop agreeing where their common edge is.
  for (let row = 0; row < field.rows; row++) {
    for (let col = 0; col < field.cols; col++) {
      const point = ground(col * field.cell, row * field.cell);
      positions.push(point.x, point.y, point.z);
    }
  }

  for (let row = 0; row < field.rows - 1; row++) {
    for (let col = 0; col < field.cols - 1; col++) {
      const topLeft = row * field.cols + col;
      const bottomLeft = topLeft + field.cols;
      indices.push(
        topLeft,
        bottomLeft,
        topLeft + 1,
        topLeft + 1,
        bottomLeft,
        bottomLeft + 1,
      );
    }
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute(
    "position",
    new THREE.Float32BufferAttribute(positions, 3),
  );
  geometry.setAttribute(
    "color",
    new THREE.Float32BufferAttribute(
      vertexColours(surface, seeds, highlight, palette),
      3,
    ),
  );
  geometry.setIndex(indices);
  geometry.computeVertexNormals();
  return new THREE.Mesh(geometry, groundMaterial(surface.projection));
}

/**
 * The plane is wrapped over less than a full hemisphere of latitude, because at the pole an equirectangular wrap
 * crushes a whole row of it into one point. That leaves a cap open at either end, through which the reader would
 * otherwise see the page behind the globe. This fills them with the deepest water the ramp has, which is what the
 * ground either side of the cap is painted anyway.
 */
function polarCap(surface: Surface, palette: GroundPalette): THREE.Mesh {
  const geometry = new THREE.SphereGeometry(
    capRadius(surface),
    CAP_SEGMENTS.around,
    CAP_SEGMENTS.down,
  );
  const deep = groundColourAt(DEEPEST, palette);
  const colours: number[] = [];
  for (
    let vertex = 0;
    vertex < geometry.getAttribute("position").count;
    vertex++
  ) {
    pushColour(colours, deep.r, deep.g, deep.b);
  }
  geometry.setAttribute("color", new THREE.Float32BufferAttribute(colours, 3));
  return new THREE.Mesh(geometry, groundMaterial("globe"));
}

export interface GroundLayer {
  meshes: THREE.Mesh[];
  dispose: () => void;
}

/** The ground the marks stand on: the heightfield itself, and under the globe the cap that closes its poles. */
export function groundLayer(
  surface: Surface,
  topics: PlacedTopic[],
  marks: PlacedContribution[],
  highlight: ReadonlySet<string> | null = null,
): GroundLayer {
  const seeds = regionSeeds(topics, marks);
  const palette = groundPalette();
  const ground = heightfieldMesh(surface, seeds, highlight, palette);
  const meshes =
    surface.projection === "globe"
      ? [polarCap(surface, palette), ground]
      : [ground];
  stampGround(meshes);

  return {
    meshes,
    dispose: () => {
      for (const mesh of meshes) {
        mesh.geometry.dispose();
        (mesh.material as THREE.Material).dispose();
      }
    },
  };
}
