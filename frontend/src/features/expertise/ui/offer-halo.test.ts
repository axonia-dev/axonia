import * as THREE from "three";
import { describe, expect, it } from "vitest";

import { haloLoops, offerHalos } from "@/features/expertise/ui/offer-halo";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

function topic(
  tag: string,
  declarations: number,
  practised = true,
): PlacedTopic {
  return { tag, x: 100, y: 100, r: 20, declarations, practised };
}

describe("offerHalos", () => {
  it("rings only an area someone offered to answer on", () => {
    expect(
      offerHalos([topic("kafka", 0), topic("postgres", 3)]).map((h) => h.tag),
    ).toEqual(["postgres"]);
  });

  it("rings wider where more people offered", () => {
    const [few, many] = offerHalos([topic("a", 1), topic("b", 9)]);

    expect(many!.radius).toBeGreaterThan(few!.radius);
  });

  it("rings an area nobody has practised, which has no circle of its own", () => {
    expect(offerHalos([topic("rust", 2, false)])).toHaveLength(1);
  });

  it("centres the ring on its area", () => {
    const [halo] = offerHalos([topic("kafka", 2)]);

    expect(halo).toMatchObject({ cx: 100, cy: 100 });
  });

  it("stops widening once the ring would swallow its neighbours", () => {
    const [halo] = offerHalos([topic("everything", 400)]);

    expect(halo!.radius).toBe(64);
  });
});

const place = (x: number, y: number) => new THREE.Vector3(x, 0, y);

describe("haloLoops", () => {
  it("draws nothing where nobody offered", () => {
    expect(haloLoops([topic("kafka", 0)], place).loops).toHaveLength(0);
  });

  it("closes the ring with enough segments to read as a circle", () => {
    const [loop] = haloLoops([topic("kafka", 3)], place).loops;

    expect(loop!.geometry.getAttribute("position").count).toBe(96);
  });

  it("computes the line distances, without which the dash draws solid", () => {
    const [loop] = haloLoops([topic("kafka", 3)], place).loops;

    expect(loop!.geometry.getAttribute("lineDistance")).toBeDefined();
  });

  /** A ring belongs to no neighbourhood, so it has a colour of its own - and a light page needs a darker one than
   *  a dark page does, or a hairline dashed circle is invisible on it. */
  it("takes its colour from the theme the page is wearing", () => {
    document.documentElement.style.setProperty("--map-ring", "#5b3fa0");
    const [loop] = haloLoops([topic("kafka", 3)], place).loops;
    document.documentElement.style.removeProperty("--map-ring");

    expect(
      (loop!.material as THREE.LineDashedMaterial).color.getHexString(),
    ).toBe("5b3fa0");
  });
});
