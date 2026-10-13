import { describe, expect, it } from "vitest";

import { applyCues } from "@/components/patterns/ceremony/cues";
import {
  flierPose,
  welcomeCues,
} from "@/features/community/ui/ceremony/welcome-choreography";

function card(): HTMLElement {
  const root = document.createElement("div");
  root.innerHTML = `
    <div data-ceremony-part="surface">
      <div data-ceremony-part="badge"></div>
      <div data-ceremony-part="meta">
        <span data-ceremony-part="facepile">
          <span data-facepile-item id="newcomer"></span>
          <span data-facepile-item id="other"></span>
        </span>
      </div>
      <h1 data-ceremony-part="name">Platform Engineering</h1>
    </div>`;
  return root;
}

function styleOf(root: HTMLElement, selector: string): CSSStyleDeclaration {
  return (root.querySelector(selector) as HTMLElement).style;
}

describe("welcomeCues", () => {
  it("keeps the doors shut at the start", () => {
    // given
    const root = card();

    // when
    applyCues(root, welcomeCues(), 0);

    // then
    expect(styleOf(root, '[data-ceremony-part="surface"]').clipPath).toBe(
      "inset(0 50.00% 0 50.00%)",
    );
  });

  it("has the doors fully open once they have opened", () => {
    // given
    const root = card();

    // when
    applyCues(root, welcomeCues(), 1.6);

    // then
    expect(styleOf(root, '[data-ceremony-part="surface"]').clipPath).toBe(
      "inset(0 0.00% 0 0.00%)",
    );
  });

  it("keeps the newcomer's face empty until the avatar lands", () => {
    // given
    const root = card();

    // when
    applyCues(root, welcomeCues(), 3.2);

    // then
    expect(styleOf(root, "#newcomer").opacity).toBe("0");
  });

  it("shows the newcomer's face once the avatar has landed", () => {
    // given
    const root = card();

    // when
    applyCues(root, welcomeCues(), 3.4);

    // then
    expect(styleOf(root, "#newcomer").opacity).toBe("1");
  });

  it("slides the other faces over to make room", () => {
    // given
    const root = card();

    // when
    applyCues(root, welcomeCues(), 2.6);

    // then
    expect(styleOf(root, "#other").transform).toBe("translateX(-20.00px)");
  });
});

describe("flierPose", () => {
  const from = { x: 384, y: 100 };
  const to = { x: 40, y: 260 };

  it("is invisible before it is born", () => {
    // when
    const actual = flierPose(2.0, from, to);

    // then
    expect(actual.opacity).toBe(0);
  });

  it("waits above the scene before flying", () => {
    // when
    const actual = flierPose(2.5, from, to);

    // then
    expect(actual.x).toBeCloseTo(384);
    expect(actual.y).toBeCloseTo(100);
  });

  it("ends on its face, at face size, and hands over to it", () => {
    // when
    const actual = flierPose(3.36, from, to);

    // then
    expect(actual.x).toBeCloseTo(40);
    expect(actual.y).toBeCloseTo(260);
    expect(actual.size).toBeCloseTo(28);
    expect(actual.opacity).toBe(0);
  });
});
