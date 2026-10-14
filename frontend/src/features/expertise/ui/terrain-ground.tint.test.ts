import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { paintNow } from "@/features/expertise/ui/echo-blend";
import { groundPalette } from "@/features/expertise/ui/ground-palette";
import { accentHexOf } from "@/features/expertise/ui/group-accents";
import type { MapHighlight } from "@/features/expertise/ui/map-highlight";
import {
  groundLayer,
  pushColour,
} from "@/features/expertise/ui/terrain-ground";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import {
  groundColourAt,
  nearestRegion,
  regionSeeds,
} from "@/features/expertise/ui/terrain-shading";
import type { Surface } from "@/features/expertise/ui/terrain-surface";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

const size = { width: 400, height: 300 };

const topic = (tag: string, x: number): PlacedTopic => ({
  tag,
  x,
  y: 150,
  r: 10,
  declarations: 0,
  practised: true,
});

const mark = (
  signalId: number,
  tag: string,
  x: number,
  slot: number,
): PlacedContribution => ({
  signalId,
  tag,
  x,
  y: 150,
  slot,
  sourceType: "SLOT",
  userId: "u1",
  occurredAt: 0,
  reach: null,
});

const topics = [topic("kafka", 100), topic("rust", 300)];
const marks = [mark(1, "kafka", 100, 0), mark(2, "rust", 300, 1)];
const surface: Surface = {
  field: buildHeightfield(marks, size),
  size,
  projection: "flat",
};
const onKafka: MapHighlight = { tags: new Set(["kafka"]), marks: null };

/** The vertex under an area's centre, which is as deep inside its region as the grid gets. */
function vertexAt(x: number, y: number): number {
  const { field } = surface;
  return Math.round(y / field.cell) * field.cols + Math.round(x / field.cell);
}

function expectedAt(vertex: number, dim: boolean): Float32Array {
  const { field } = surface;
  const col = vertex % field.cols;
  const row = Math.floor(vertex / field.cols);
  const region = nearestRegion(
    regionSeeds(topics, marks),
    col * field.cell,
    row * field.cell,
  )!;
  const colour = new Float32Array(3);
  pushColour(
    colour,
    0,
    groundColourAt(
      {
        height: field.values[vertex]!,
        accent: accentHexOf(region.slot),
        nearness: region.nearness,
        dim,
      },
      groundPalette(),
    ),
  );
  return colour;
}

const rgbOf = (values: Float32Array, vertex: number) =>
  values.slice(vertex * 3, vertex * 3 + 3);

describe("the ground's tint", () => {
  it("sets back the ground a highlight is not about, and only that", () => {
    const { tint } = groundLayer(surface, topics, marks);
    const target = tint.targetFor(onKafka);
    const rust = vertexAt(300, 150);
    const kafka = vertexAt(100, 150);

    expect(rgbOf(target, rust)).toStrictEqual(expectedAt(rust, true));
    expect(rgbOf(target, kafka)).toStrictEqual(expectedAt(kafka, false));
  });

  it("places each vertex at its own grid point", () => {
    const { tint } = groundLayer(surface, topics, marks);
    const { field } = surface;

    expect(tint.stride).toBe(3);
    expect(tint.xy).toHaveLength(field.cols * field.rows * 2);
    expect([
      tint.xy[(field.cols + 1) * 2],
      tint.xy[(field.cols + 1) * 2 + 1],
    ]).toStrictEqual([field.cell, field.cell]);
  });

  it("writes a painted highlight straight into the mesh it drew", () => {
    const layer = groundLayer(surface, topics, marks);
    const colour = layer.meshes[0]!.geometry.getAttribute(
      "color",
    ) as THREE.BufferAttribute;
    const before = colour.version;

    paintNow(layer.tint, onKafka);

    expect(colour.array).toStrictEqual(layer.tint.targetFor(onKafka));
    expect(colour.version).toBeGreaterThan(before);
  });

  it("starts the mesh at the whole map", () => {
    const layer = groundLayer(surface, topics, marks);

    expect(layer.meshes[0]!.geometry.getAttribute("color").array).toStrictEqual(
      layer.tint.targetFor({ tags: null, marks: null }),
    );
  });
});
