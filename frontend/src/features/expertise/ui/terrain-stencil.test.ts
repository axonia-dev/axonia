import * as THREE from "three";
import { describe, expect, it } from "vitest";

import {
  stampGround,
  standOnGround,
} from "@/features/expertise/ui/terrain-stencil";

describe("terrain stencil", () => {
  it("draws the ground first and stamps every material it is drawn with", () => {
    const materials = [
      new THREE.MeshBasicMaterial(),
      new THREE.MeshBasicMaterial(),
    ];
    const ground = new THREE.Mesh(new THREE.BufferGeometry(), materials);

    stampGround([ground]);

    expect(ground.renderOrder).toBeLessThan(0);
    for (const material of materials) {
      expect(material.stencilWrite).toBe(true);
      expect(material.stencilFunc).toBe(THREE.AlwaysStencilFunc);
      expect(material.stencilZPass).toBe(THREE.ReplaceStencilOp);
    }
  });

  it("lets what stands on the ground draw only where the ground was stamped, and leaves the stamp alone", () => {
    const mark = new THREE.Points(
      new THREE.BufferGeometry(),
      new THREE.PointsMaterial(),
    );
    const ground = new THREE.Mesh(
      new THREE.BufferGeometry(),
      new THREE.MeshBasicMaterial(),
    );
    stampGround([ground]);

    standOnGround([mark]);

    expect(mark.material.stencilWrite).toBe(true);
    expect(mark.material.stencilFunc).toBe(THREE.EqualStencilFunc);
    expect(mark.material.stencilRef).toBe(ground.material.stencilRef);
    expect(mark.material.stencilZPass).toBe(THREE.KeepStencilOp);
  });
});
