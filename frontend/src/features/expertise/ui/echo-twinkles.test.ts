import * as THREE from "three";
import { describe, expect, it } from "vitest";

import { ECHO } from "@/features/expertise/ui/echo-timeline";
import { twinkleLayer } from "@/features/expertise/ui/echo-twinkles";

const layer = () =>
  twinkleLayer({
    points: [
      { x: 10, y: 20, reachedAtMs: 300 },
      { x: 40, y: 50, reachedAtMs: 900 },
    ],
    place: (x, y) => new THREE.Vector3(x, 0, y),
    markPx: 6,
    pixelRatio: 2,
    colour: new THREE.Color(1, 0, 0),
  });

const uniformsOf = (points: THREE.Points) =>
  (points.material as THREE.ShaderMaterial).uniforms;

describe("twinkleLayer", () => {
  it("holds one placed point per twinkle with the moment its ring reaches it", () => {
    const { object } = layer();
    const geometry = object.geometry;
    expect([...geometry.getAttribute("aReached").array]).toEqual([300, 900]);
    expect(geometry.getAttribute("position").getX(1)).toBe(40);
    expect(geometry.getAttribute("position").getZ(1)).toBe(50);
    expect(uniformsOf(object)["uDuration"]!.value).toBe(ECHO.twinkleMs);
  });

  it("moves its clock without touching the geometry", () => {
    const twinkles = layer();
    twinkles.at(1200);
    expect(uniformsOf(twinkles.object)["uElapsed"]!.value).toBe(1200);
  });

  it("is sized in device pixels", () => {
    const twinkles = layer();
    expect(uniformsOf(twinkles.object)["uPx"]!.value).toBe(12);
    twinkles.resize(8);
    expect(uniformsOf(twinkles.object)["uPx"]!.value).toBe(16);
  });
});
