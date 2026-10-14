import * as THREE from "three";
import { describe, expect, it } from "vitest";

import { echoInk } from "@/features/expertise/ui/echo-rings";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import { twinkleLayer } from "@/features/expertise/ui/echo-twinkles";

const layer = () =>
  twinkleLayer({
    points: [
      { x: 10, y: 20, reachedAtMs: 300, slot: 0 },
      { x: 40, y: 50, reachedAtMs: 900, slot: 3 },
    ],
    place: (x, y) => new THREE.Vector3(x, 0, y),
    markPx: 6,
    pixelRatio: 2,
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
  /** A twinkle on a graphql mark in kafka's ink would say the answer was somewhere it is not. */
  it("draws each twinkle in its own area's ink", () => {
    const colour = layer().object.geometry.getAttribute("aColour");
    const inkAt = (i: number) =>
      new THREE.Color().fromBufferAttribute(colour, i).getHexString();
    expect(inkAt(0)).toBe(echoInk(0).getHexString());
    expect(inkAt(1)).toBe(echoInk(3).getHexString());
  });
});
