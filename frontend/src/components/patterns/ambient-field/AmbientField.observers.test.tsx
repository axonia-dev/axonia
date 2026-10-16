import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  captureObserver,
  hideTab,
  reducedMotion,
  renderField,
} from "@/components/patterns/ambient-field/ambient-field.test-helpers";

const intersecting = (isIntersecting: boolean) =>
  [{ isIntersecting }] as never[];

function sizeHost(width: number, height: number) {
  const host = screen.getByTestId("host");
  vi.spyOn(host, "clientWidth", "get").mockReturnValue(width);
  vi.spyOn(host, "clientHeight", "get").mockReturnValue(height);
}

const colourStops = (stops: { mock: { calls: unknown[][] } }) =>
  stops.mock.calls.map(([, colour]) => String(colour));

beforeEach(() => reducedMotion(false));
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("AmbientField off screen", () => {
  it("draws nothing while the field is scrolled out of view", () => {
    const onScreen = captureObserver("IntersectionObserver");
    const { run, frames } = renderField();
    onScreen.fire(intersecting(false));
    const away = frames();
    run(16 * 10);
    expect(frames()).toBe(away);
    onScreen.fire(intersecting(true));
    run(16 * 2);
    expect(frames()).toBeGreaterThan(away);
  });

  it("follows the latest crossing when several arrive at once", () => {
    const onScreen = captureObserver("IntersectionObserver");
    const { run, frames } = renderField();
    onScreen.fire([...intersecting(false), ...intersecting(true)]);
    const before = frames();
    run(16 * 2);
    expect(frames()).toBeGreaterThan(before);
  });

  it("stays paused when the tab returns while the field is still off screen", () => {
    const onScreen = captureObserver("IntersectionObserver");
    const { run, frames } = renderField();
    onScreen.fire(intersecting(false));
    hideTab(true);
    hideTab(false);
    const back = frames();
    run(16 * 10);
    expect(frames()).toBe(back);
  });
});

describe("AmbientField on resize", () => {
  it("repaints its still frame at the new size under reduced motion", () => {
    reducedMotion(true);
    const resized = captureObserver("ResizeObserver");
    const { container, frames } = renderField();
    const still = frames();
    sizeHost(400, 300);
    resized.fire([]);
    const canvas = container.querySelector("canvas");
    expect(frames()).toBe(still * 2);
    expect(canvas?.width).toBe(400);
    expect(canvas?.height).toBe(300);
  });

  it("paints nothing while its host has no size, then paints once it has one", () => {
    reducedMotion(true);
    const resized = captureObserver("ResizeObserver");
    const { frames } = renderField({}, { width: 320, height: 0 });
    expect(frames()).toBe(0);
    sizeHost(320, 180);
    resized.fire([]);
    expect(frames()).toBeGreaterThan(0);
  });

  it("leaves the frame alone when the size did not change", () => {
    reducedMotion(true);
    const resized = captureObserver("ResizeObserver");
    const { frames } = renderField();
    const still = frames();
    resized.fire([]);
    expect(frames()).toBe(still);
  });
});

describe("AmbientField inks", () => {
  it("repaints in the new ink when the accent or the inks change", () => {
    reducedMotion(true);
    const { context, rerender } = renderField();
    const accent = colourStops(context.addColorStop);
    context.addColorStop.mockClear();
    rerender({ inks: { upper: "light", lower: "light" } });
    const light = colourStops(context.addColorStop);
    context.addColorStop.mockClear();
    rerender({ inks: { upper: "light", lower: "light" }, hue: "#10b981" });
    const green = colourStops(context.addColorStop);
    expect(light).not.toEqual(accent);
    expect(green).not.toEqual(light);
  });
});
