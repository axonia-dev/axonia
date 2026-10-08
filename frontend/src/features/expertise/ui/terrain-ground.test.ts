import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import { groundLayer, placeAt } from "@/features/expertise/ui/terrain-ground";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import {
  GLOBE_RADIUS,
  type Surface,
} from "@/features/expertise/ui/terrain-surface";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

const size = { width: 400, height: 300 };

const topics: PlacedTopic[] = [
  { tag: "kafka", x: 200, y: 150, r: 10, declarations: 0, practised: true },
];

function mark(x: number, y: number, signalId = 1): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x,
    y,
    slot: 0,
    sourceType: "SLOT",
    userId: "u1",
    occurredAt: 0,
    reach: null,
  };
}

function surfaceOf(
  projection: Projection,
  marks: PlacedContribution[] = [],
): Surface {
  return { field: buildHeightfield(marks, size), size, projection };
}

type Channels = THREE.BufferAttribute | THREE.InterleavedBufferAttribute;

function rgbAt(attribute: Channels, index: number): number[] {
  return [
    attribute.getX(index),
    attribute.getY(index),
    attribute.getZ(index),
  ].map((channel) => Math.round(channel * 255));
}

function distinctColours(attribute: Channels): number {
  const seen = new Set<string>();
  for (let i = 0; i < attribute.count; i++)
    seen.add(rgbAt(attribute, i).join());
  return seen.size;
}

/** The grid vertex the density piled highest on, which is the one that has to read as a summit. */
function indexOfHighest(surface: Surface): number {
  const { values } = surface.field;
  let best = 0;
  for (let i = 0; i < values.length; i++) {
    if (values[i]! > values[best]!) best = i;
  }
  return best;
}

/** How high a mesh reaches, which on the globe is how near the pole it gets. */
function topOf(mesh: THREE.Mesh): number {
  const position = mesh.geometry.getAttribute("position");
  let top = Number.NEGATIVE_INFINITY;
  for (let i = 0; i < position.count; i++)
    top = Math.max(top, position.getY(i));
  return top;
}

describe("groundLayer", () => {
  it("shares a vertex between the cells that meet on it", () => {
    // Lifting per cell tears the mesh into ribbons, and the tear only shows where the ground is not level.
    const { field } = surfaceOf("flat", [mark(200, 150)]);
    const [ground] = groundLayer(surfaceOf("flat", [mark(200, 150)]), topics, [
      mark(200, 150),
    ]).meshes;

    expect(ground!.geometry.getAttribute("position").count).toBe(
      field.cols * field.rows,
    );
  });

  /** Height is a colour here, not a displacement: the globe was a stack of mesas while it was geometry. */
  it("leaves the ground level and lets its colour carry the height", () => {
    const [ground] = groundLayer(surfaceOf("flat", [mark(200, 150)]), topics, [
      mark(200, 150),
    ]).meshes;

    expect(topOf(ground!)).toBe(0);
  });

  /**
   * The flat reading's every face points at the reader, so a light adds nothing to it but its own intensity, and
   * over one that clips the ramp towards white - which turned a blue sea grey. The globe needs the light: it is
   * what makes a sphere read as a sphere rather than as a disc.
   */
  it("draws the flat ground unlit and the globe's lit", () => {
    const flat = groundLayer(surfaceOf("flat", [mark(200, 150)]), topics, [
      mark(200, 150),
    ]).meshes;
    const globe = groundLayer(surfaceOf("globe"), topics, []).meshes;

    for (const mesh of flat) {
      expect(mesh.material).toBeInstanceOf(THREE.MeshBasicMaterial);
    }
    for (const mesh of globe) {
      expect(mesh.material).toBeInstanceOf(THREE.MeshStandardMaterial);
    }
  });

  it("closes the cap the wrap leaves open at the pole", () => {
    // Latitude is deliberately compressed short of the pole, because a full wrap crushes a whole row of the plane
    // into one point. The ground alone therefore stops short and the reader sees the page through the hole.
    const tops = groundLayer(surfaceOf("globe"), topics, [])
      .meshes.map(topOf)
      .sort((a, b) => b - a);

    expect(tops).toHaveLength(2);
    expect(tops[0]).toBeGreaterThan(GLOBE_RADIUS * 0.98);
    expect(tops[1]).toBeLessThan(GLOBE_RADIUS * 0.96);
  });

  it("keeps that cap under the ground rather than through it", () => {
    const tops = groundLayer(surfaceOf("globe"), topics, []).meshes.map(topOf);

    expect(Math.max(...tops)).toBeLessThan(GLOBE_RADIUS);
  });

  it("sinks the cap no deeper than the ground's own facets need, so it leaves no dent in the limb", () => {
    const [cap, ground] = groundLayer(surfaceOf("globe"), topics, []).meshes;
    const corners = (mesh: THREE.Mesh) => {
      const position = mesh.geometry.getAttribute("position");
      return Array.from({ length: position.count }, (_, i) =>
        new THREE.Vector3().fromBufferAttribute(position, i),
      );
    };
    // A quad's centre is where its flat face sags furthest from the sphere: the middle of its diagonal.
    const index = ground!.geometry.getIndex()!;
    const points = corners(ground!);
    const quadCentres = Array.from({ length: index.count / 3 }, (_, t) =>
      points[index.getX(t * 3 + 1)]!.clone()
        .add(points[index.getX(t * 3 + 2)]!)
        .multiplyScalar(0.5),
    );
    const capTop = Math.max(...corners(cap!).map((p) => p.length()));

    expect(capTop).toBeLessThan(
      Math.min(...quadCentres.map((p) => p.length())),
    );
    expect(capTop).toBeGreaterThan(GLOBE_RADIUS - 1);
  });

  it("has no cap to draw under the flat reading", () => {
    expect(groundLayer(surfaceOf("flat"), topics, []).meshes).toHaveLength(1);
  });

  // The relief IS the reading. Under one flat ground colour, a heightfield lit by a single raking light is a dark
  // blob whose hills read as noise, which is exactly what shipped.
  it("paints the ground rather than handing the whole mesh one colour", () => {
    const marks = [mark(200, 150), mark(204, 152, 2), mark(198, 148, 3)];
    const [ground] = groundLayer(
      surfaceOf("flat", marks),
      topics,
      marks,
    ).meshes;

    const colour = ground!.geometry.getAttribute("color");
    expect(colour.count).toBe(ground!.geometry.getAttribute("position").count);
    expect(distinctColours(colour)).toBeGreaterThan(1);
  });

  it("paints the summit differently from the open water around it", () => {
    const marks = [mark(200, 150), mark(204, 152, 2), mark(198, 148, 3)];
    const surface = surfaceOf("flat", marks);
    const [ground] = groundLayer(surface, topics, marks).meshes;
    const colour = ground!.geometry.getAttribute("color");

    const peak = indexOfHighest(surface);
    expect(rgbAt(colour, peak)).not.toStrictEqual(rgbAt(colour, 0));
  });

  // Without this, changing the reading beside the map changed nothing a reader could see on it.
  it("sets back the ground a reading is not about", () => {
    const marks = [mark(200, 150), mark(204, 152, 2), mark(198, 148, 3)];
    const surface = surfaceOf("flat", marks);
    const peak = indexOfHighest(surface);
    const colourOf = (highlight: ReadonlySet<string> | null) =>
      rgbAt(
        groundLayer(
          surface,
          topics,
          marks,
          highlight,
        ).meshes[0]!.geometry.getAttribute("color"),
        peak,
      );

    const lit = colourOf(new Set(["kafka"]));
    const dim = colourOf(new Set(["flink"]));

    expect(dim).not.toStrictEqual(lit);
    expect(dim.reduce((a, b) => a + b, 0)).toBeLessThan(
      lit.reduce((a, b) => a + b, 0),
    );
    expect(colourOf(null)).toStrictEqual(lit);
  });

  /** Water carries no neighbourhood's colour to turn down, so dimming it would only darken the map around a
   *  reading rather than pick anything out of it. */
  it("leaves the open water alone whatever the reading is about", () => {
    const marks = [mark(200, 150)];
    const surface = surfaceOf("flat", marks);
    const colourAt = (highlight: ReadonlySet<string> | null) =>
      rgbAt(
        groundLayer(
          surface,
          topics,
          marks,
          highlight,
        ).meshes[0]!.geometry.getAttribute("color"),
        0,
      );

    expect(colourAt(new Set(["flink"]))).toStrictEqual(colourAt(null));
  });

  it("releases every geometry and material it made", () => {
    const layer = groundLayer(surfaceOf("globe"), topics, []);
    let released = 0;
    const count = () => {
      released += 1;
    };
    for (const mesh of layer.meshes) {
      mesh.geometry.addEventListener("dispose", count);
      (mesh.material as THREE.Material).addEventListener("dispose", count);
    }

    layer.dispose();

    expect(released).toBe(layer.meshes.length * 2);
  });
});

describe("placeAt", () => {
  it("lifts a point clear of the ground it stands on", () => {
    const surface = surfaceOf("flat", [mark(200, 150)]);

    expect(placeAt(surface, 9)(200, 150).y).toBeCloseTo(
      placeAt(surface, 0)(200, 150).y + 9,
      6,
    );
  });

  it("wraps the same coordinate onto the sphere under the globe", () => {
    const at = placeAt(surfaceOf("globe"), 0)(size.width / 2, size.height / 2);

    expect(at.length()).toBeCloseTo(GLOBE_RADIUS, 6);
    expect(at).toBeInstanceOf(THREE.Vector3);
  });
});
