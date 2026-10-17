import { describe, expect, it } from "vitest";

import { inViewport } from "@/features/recognition/ui/viewport";

function at(rect: Partial<DOMRect>): Element {
  const el = document.createElement("span");
  el.getBoundingClientRect = () =>
    ({
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
      width: 0,
      height: 0,
      ...rect,
    }) as DOMRect;
  return el;
}

const VISIBLE = { top: 100, bottom: 116, left: 100, right: 116, width: 16 };

describe("inViewport", () => {
  it("sees an element drawn inside the window", () => {
    expect(inViewport(at(VISIBLE))).toBe(true);
  });

  it("ignores an element that takes no width", () => {
    expect(inViewport(at({ ...VISIBLE, width: 0 }))).toBe(false);
  });

  it("ignores an element above, below, left or right of the window", () => {
    expect(inViewport(at({ ...VISIBLE, top: -40, bottom: -24 }))).toBe(false);
    expect(
      inViewport(
        at({
          ...VISIBLE,
          top: window.innerHeight,
          bottom: window.innerHeight + 16,
        }),
      ),
    ).toBe(false);
    expect(inViewport(at({ ...VISIBLE, left: -40, right: -24 }))).toBe(false);
    expect(
      inViewport(
        at({
          ...VISIBLE,
          left: window.innerWidth,
          right: window.innerWidth + 16,
        }),
      ),
    ).toBe(false);
  });
});
