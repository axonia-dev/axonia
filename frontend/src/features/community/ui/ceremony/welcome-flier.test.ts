import { describe, expect, it } from "vitest";

import {
  flierAnchor,
  flierPose,
  placeFlier,
} from "@/features/community/ui/ceremony/welcome-choreography";

interface Layout {
  readonly top: number;
  readonly banner?: DOMRect;
  readonly faceTop?: number;
}

/** A card 800 px wide drawn at half size, `top` pixels below the viewport's edge. */
function cardAt({ top, banner, faceTop = 100 }: Layout): HTMLElement {
  const root = document.createElement("div");
  root.innerHTML = `
    ${banner === undefined ? "" : '<div data-ceremony-part="banner"></div>'}
    <span data-ceremony-part="facepile">
      <span data-facepile-item></span>
    </span>
    <span data-ceremony-flier></span>`;
  Object.defineProperty(root, "offsetWidth", { value: 800 });
  Object.defineProperty(root, "offsetHeight", { value: 400 });
  root.getBoundingClientRect = () => new DOMRect(100, top, 400, 200);
  const face = root.querySelector("[data-facepile-item]") as HTMLElement;
  face.getBoundingClientRect = () => new DOMRect(120, top + faceTop, 14, 14);
  const bannerElement = root.querySelector<HTMLElement>(
    '[data-ceremony-part="banner"]',
  );
  if (bannerElement !== null && banner !== undefined) {
    bannerElement.getBoundingClientRect = () => banner;
  }
  return root;
}

function flierOf(root: HTMLElement): HTMLElement {
  return root.querySelector("[data-ceremony-flier]") as HTMLElement;
}

describe("flierAnchor", () => {
  it("is the centre of the banner, in the card's own pixels", () => {
    // given
    const root = cardAt({ top: 50, banner: new DOMRect(100, 50, 400, 100) });

    // when
    const actual = flierAnchor(root);

    // then
    expect(actual).toEqual({ x: 400, y: 100 });
  });

  it("is above a card without a banner", () => {
    // given
    const root = cardAt({ top: 300 });

    // when
    const actual = flierAnchor(root);

    // then
    expect(actual).toEqual({ x: 400, y: -56 });
  });

  it("comes down into the card where there is no room above it", () => {
    // given
    const root = cardAt({ top: 10 });

    // when
    const actual = flierAnchor(root);

    // then
    expect(actual.y).toBe(20);
  });
});

describe("flierPose travel", () => {
  it("reports how far along its flight the avatar is", () => {
    // when
    const actual = [
      flierPose(2.5, { x: 0, y: 0 }, { x: 10, y: 10 }).travel,
      flierPose(3.35, { x: 0, y: 0 }, { x: 10, y: 10 }).travel,
    ];

    // then
    expect(actual).toEqual([0, 1]);
  });

  it("lifts only as far as it is told", () => {
    // when
    const actual = flierPose(2.925, { x: 0, y: 0 }, { x: 0, y: 0 }, 30);

    // then
    expect(actual.y).toBeCloseTo(-30);
  });
});

describe("placeFlier", () => {
  it("sizes the initials with the avatar, landing on the face's own", () => {
    // given
    const root = cardAt({ top: 300 });

    // when
    placeFlier(root, 3.34);

    // then
    expect(parseFloat(flierOf(root).style.fontSize)).toBeCloseTo(10, 0);
  });

  it("starts with a white ring", () => {
    // given
    const root = cardAt({ top: 300 });

    // when
    placeFlier(root, 2.4);

    // then
    expect(flierOf(root).style.getPropertyValue("--flier-ring")).toBe(
      "color-mix(in srgb, #ffffff 100.0%, var(--surface))",
    );
  });

  it("keeps the arc inside the viewport above a card high on the screen", () => {
    // given
    const root = cardAt({ top: 10, faceTop: 10 });

    // when
    placeFlier(root, 2.925);

    // then
    const [, y = 0] = (
      flierOf(root).style.transform.match(/-?[\d.]+/g) ?? []
    ).map(Number);
    expect(10 + y / 2).toBeGreaterThanOrEqual(0);
  });
});
