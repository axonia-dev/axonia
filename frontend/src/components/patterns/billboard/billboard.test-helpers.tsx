import { act, screen } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";

import { Billboard } from "./Billboard";

export const WIDTH = 800;
const originalMatchMedia = window.matchMedia;
const clientWidth = Object.getOwnPropertyDescriptor(
  HTMLElement.prototype,
  "clientWidth",
);

type Slide = { id: string; name: string };
export const slides: Slide[] = [
  { id: "a", name: "Alpha" },
  { id: "b", name: "Bravo" },
  { id: "c", name: "Charlie" },
];

export function stubReducedMotion(reduced: boolean) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: query.includes("prefers-reduced-motion") && reduced,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

export function billboard(
  items: readonly Slide[] = slides,
  props: Partial<
    Pick<
      Parameters<typeof Billboard<Slide>>[0],
      | "itemLabel"
      | "controlsTone"
      | "suspended"
      | "controlsClassName"
      | "initialIndex"
    >
  > = {},
) {
  return (
    <Billboard
      label="Featured"
      items={items}
      {...props}
      renderSlide={(item, active) => (
        <div data-active={active}>
          <h2>{item.name}</h2>
          <button type="button">Join {item.name}</button>
        </div>
      )}
    />
  );
}

export function slide(position: number): HTMLElement {
  return screen.getByRole("group", { name: `${position} of 3` });
}

export function isInert(element: HTMLElement): boolean {
  return element.hasAttribute("inert");
}

export function track(): HTMLElement {
  const first = slide(1);
  if (first.parentElement === null) throw new Error("no track");
  return first.parentElement;
}

export function placeTrack(left: number) {
  Object.defineProperty(track(), "scrollLeft", {
    configurable: true,
    writable: true,
    value: left,
  });
}

export function advance(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

/** Stubs the layout jsdom lacks; returns the `scrollTo` spy. */
export function stubBillboardStage(): { scrollTo: ReturnType<typeof vi.fn> } {
  const stage = { scrollTo: vi.fn() };
  beforeEach(() => {
    vi.useFakeTimers();
    stubReducedMotion(false);
    stage.scrollTo = vi.fn();
    Element.prototype.scrollTo = stage.scrollTo;
    Element.prototype.setPointerCapture = vi.fn();
    Object.defineProperty(HTMLElement.prototype, "clientWidth", {
      configurable: true,
      get: () => WIDTH,
    });
  });
  afterEach(() => {
    vi.useRealTimers();
    window.matchMedia = originalMatchMedia;
    if (clientWidth !== undefined)
      Object.defineProperty(HTMLElement.prototype, "clientWidth", clientWidth);
  });
  return stage;
}
