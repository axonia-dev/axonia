import * as THREE from "three";
import { describe, expect, it } from "vitest";

import { echoInk, ringLayer } from "@/features/expertise/ui/echo-rings";

const place = (x: number, y: number) => new THREE.Vector3(x, 0, y);
const ring = { x: 0, y: 0, radius: 10, alpha: 0.5, slot: 0 };

const visible = (loops: readonly THREE.LineLoop[]) =>
  loops.filter((loop) => loop.visible);

describe("ringLayer", () => {
  it("holds its whole capacity hidden until a ring is shown", () => {
    const layer = ringLayer({ place, capacity: 6 });
    expect(layer.objects).toHaveLength(6);
    expect(visible(layer.objects)).toHaveLength(0);
  });

  it("draws one ring per ring shown, at its radius and its alpha", () => {
    const layer = ringLayer({ place, capacity: 6 });
    layer.show([ring]);

    const shown = visible(layer.objects);
    expect(shown).toHaveLength(1);
    const material = shown[0]!.material as THREE.LineBasicMaterial;
    expect(material.opacity).toBe(0.5);
    expect(material.color.equals(echoInk(0))).toBe(true);
    const position = shown[0]!.geometry.getAttribute("position");
    expect(position.getX(0)).toBeCloseTo(10, 6);
    expect(position.getY(0)).toBeCloseTo(0, 6);
    expect(position.getZ(0)).toBeCloseTo(0, 6);
  });

  it("fades a ring out past the edge it is drawn within", () => {
    const layer = ringLayer({
      place,
      capacity: 1,
      within: { minX: -20, minY: -20, maxX: 5, maxY: 20 },
    });
    layer.show([ring]);
    const shown = layer.objects[0]!.geometry.getAttribute("color");
    expect(shown.getW(0)).toBe(0);
    expect(shown.getW(48)).toBe(1);
  });

  it("hides every ring when none is shown", () => {
    const layer = ringLayer({ place, capacity: 6 });
    layer.show([ring, { ...ring, radius: 20 }]);
    layer.show([]);
    expect(visible(layer.objects)).toHaveLength(0);
  });

  it("drops a ring past its capacity rather than throwing", () => {
    const layer = ringLayer({ place, capacity: 2 });
    expect(() => layer.show([ring, ring, ring])).not.toThrow();
    expect(visible(layer.objects)).toHaveLength(2);
  });
});

describe("echoInk", () => {
  it("is an area's accent carried half way to the summit", () => {
    const ink = echoInk(0);
    const white = new THREE.Color(1, 1, 1);
    expect(ink.equals(white)).toBe(false);
    expect(ink.r).toBeGreaterThan(0);
  });
});
