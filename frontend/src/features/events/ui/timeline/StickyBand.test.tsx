import { act, render, screen } from "@testing-library/react";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

import { StickyBand } from "@/features/events/ui/timeline/StickyBand";

const real = window.IntersectionObserver;
let report: (sentinelTop: number) => void = () => {};

beforeAll(() => {
  window.IntersectionObserver = class {
    constructor(private readonly callback: IntersectionObserverCallback) {}
    observe(target: Element) {
      report = (sentinelTop) =>
        this.callback(
          [
            {
              target,
              boundingClientRect: { top: sentinelTop } as DOMRectReadOnly,
              rootBounds: { top: 60 } as DOMRectReadOnly,
            } as IntersectionObserverEntry,
          ],
          this as unknown as IntersectionObserver,
        );
    }
    disconnect() {}
  } as unknown as typeof IntersectionObserver;
});

afterAll(() => {
  window.IntersectionObserver = real;
});

function setup() {
  const { container } = render(
    <StickyBand>
      <h1>Schedule</h1>
      <div role="slider" aria-label="Schedule timeline" aria-valuenow={0} />
    </StickyBand>,
  );
  const band = container.querySelector<HTMLElement>("[data-time-ribbon]");
  const cover = () =>
    container.querySelector<HTMLElement>("[data-ribbon-cover]");
  return { band, cover };
}

describe("StickyBand", () => {
  it("holds the page controls and the ribbon in one sticky band", () => {
    // when
    const { band } = setup();
    // then
    expect(band?.className).toContain("sticky");
    expect(band).toContainElement(screen.getByRole("heading"));
    expect(band).toContainElement(screen.getByRole("slider"));
  });

  it("stays clear at rest, so the band sits on the page", () => {
    // given
    const { cover } = setup();
    // when
    act(() => report(120));
    // then
    expect(cover()?.className).toContain("opacity-0");
  });

  it("covers the band once stuck under the topbar, and clears again on the way back", () => {
    // given
    const { cover } = setup();
    // when
    act(() => report(59));
    // then
    expect(cover()?.className).toContain("opacity-100");
    expect(cover()?.className).not.toContain("transition");
    // when
    act(() => report(60));
    // then
    expect(cover()?.className).toContain("opacity-0");
    expect(cover()?.className).toContain("motion-safe:transition-opacity");
  });
});
