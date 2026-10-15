import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  hideTab,
  reducedMotion,
  renderField,
} from "@/components/patterns/ambient-field/ambient-field.test-helpers";

beforeEach(() => reducedMotion(false));
afterEach(() => vi.restoreAllMocks());

describe("AmbientField", () => {
  it("is a decorative canvas nobody can click", () => {
    const { container } = renderField();
    const canvas = container.querySelector("[data-ambient-field]");
    expect(canvas).toHaveAttribute("aria-hidden", "true");
    expect(canvas).toHaveClass("pointer-events-none");
  });

  it("fades in once its first frame is drawn", () => {
    const { container, frames } = renderField();
    const canvas = container.querySelector("[data-ambient-field]");
    expect(frames()).toBeGreaterThan(0);
    expect(canvas).toHaveAttribute("data-painted", "true");
    expect(canvas).toHaveClass("duration-600");
  });

  it("animates frame after frame", () => {
    const { run, frames } = renderField();
    run(16 * 3);
    const afterThree = frames();
    run(16 * 3);
    expect(frames()).toBeGreaterThan(afterThree);
  });

  it("under reduced motion draws one still frame and no loop", () => {
    reducedMotion(true);
    const { run, frames } = renderField();
    const still = frames();
    run(16 * 10);
    expect(still).toBeGreaterThan(0);
    expect(frames()).toBe(still);
  });

  it("stops drawing once unmounted", () => {
    const { run, frames, unmount } = renderField();
    run(16 * 2);
    unmount();
    const atUnmount = frames();
    run(16 * 10);
    expect(frames()).toBe(atUnmount);
  });

  it("pauses while the tab is hidden and resumes when it returns", () => {
    const { run, frames } = renderField();
    run(16 * 2);
    hideTab(true);
    const hidden = frames();
    run(16 * 10);
    expect(frames()).toBe(hidden);
    hideTab(false);
    run(16 * 2);
    expect(frames()).toBeGreaterThan(hidden);
  });

  it("gives up animating after a run of slow frames and keeps the still frame", () => {
    const { run, frames } = renderField();
    run(1000);
    run(80 * 6, 80);
    const slowed = frames();
    run(16 * 10);
    expect(frames()).toBe(slowed);
  });

  it("forgives the slow frames of the page's own first second", () => {
    const { run, frames } = renderField();
    run(80 * 10, 80);
    const warmed = frames();
    run(16 * 3);
    expect(frames()).toBeGreaterThan(warmed);
  });
});
