import { render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AmbientField } from "@/components/patterns/ambient-field/AmbientField";
import { manualClock } from "@/components/patterns/ceremony/ceremony-stage.test-helpers";

const LIGHTS = [
  { position: 0.9, freshness: 1, size: "large" as const, blooms: false },
];
const INKS = { upper: "accent", lower: "accent" } as const;

function stubContext() {
  const arc = vi.fn();
  const context = {
    canvas: { width: 0, height: 0 },
    arc,
    save: vi.fn(),
    restore: vi.fn(),
    beginPath: vi.fn(),
    fill: vi.fn(),
    fillRect: vi.fn(),
    clearRect: vi.fn(),
    drawImage: vi.fn(),
    setTransform: vi.fn(),
    createRadialGradient: () => ({ addColorStop: vi.fn() }),
    createLinearGradient: () => ({ addColorStop: vi.fn() }),
    globalCompositeOperation: "source-over",
    fillStyle: "",
  };
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
    context as never,
  );
  return context;
}

function reducedMotion(matches: boolean) {
  vi.spyOn(window, "matchMedia").mockImplementation(
    (query: string) =>
      ({
        matches: matches && query.includes("prefers-reduced-motion"),
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }) as never,
  );
}

beforeEach(() => reducedMotion(false));
afterEach(() => vi.restoreAllMocks());

describe("AmbientField", () => {
  it("is a decorative canvas nobody can click", () => {
    stubContext();
    const { container } = render(
      <div style={{ position: "relative" }}>
        <AmbientField
          lights={LIGHTS}
          seed={1}
          hue="#6e3ceb"
          inks={INKS}
          clock={manualClock().clock}
        />
      </div>,
    );
    const canvas = container.querySelector("[data-ambient-field]");
    expect(canvas).toHaveAttribute("aria-hidden", "true");
    expect(canvas).toHaveClass("pointer-events-none");
  });

  it("animates frame after frame", () => {
    const context = stubContext();
    const { clock, run } = manualClock();
    render(
      <AmbientField
        lights={LIGHTS}
        seed={1}
        hue="#6e3ceb"
        inks={INKS}
        clock={clock}
      />,
    );
    run(16 * 3);
    const afterThree = context.arc.mock.calls.length;
    run(16 * 3);
    expect(context.arc.mock.calls.length).toBeGreaterThan(afterThree);
  });

  it("under reduced motion draws one still frame and no loop", () => {
    reducedMotion(true);
    const context = stubContext();
    const { clock, run } = manualClock();
    render(
      <AmbientField
        lights={LIGHTS}
        seed={1}
        hue="#6e3ceb"
        inks={INKS}
        clock={clock}
      />,
    );
    const still = context.arc.mock.calls.length;
    run(16 * 10);
    expect(still).toBeGreaterThan(0);
    expect(context.arc.mock.calls).toHaveLength(still);
  });

  it("stops drawing once unmounted", () => {
    const context = stubContext();
    const { clock, run } = manualClock();
    const { unmount } = render(
      <AmbientField
        lights={LIGHTS}
        seed={1}
        hue="#6e3ceb"
        inks={INKS}
        clock={clock}
      />,
    );
    run(16 * 2);
    unmount();
    const atUnmount = context.arc.mock.calls.length;
    run(16 * 10);
    expect(context.arc.mock.calls).toHaveLength(atUnmount);
  });

  it("pauses while the tab is hidden and resumes when it returns", () => {
    const context = stubContext();
    const { clock, run } = manualClock();
    render(
      <AmbientField
        lights={LIGHTS}
        seed={1}
        hue="#6e3ceb"
        inks={INKS}
        clock={clock}
      />,
    );
    run(16 * 2);
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    document.dispatchEvent(new Event("visibilitychange"));
    const hidden = context.arc.mock.calls.length;
    run(16 * 10);
    expect(context.arc.mock.calls).toHaveLength(hidden);
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    document.dispatchEvent(new Event("visibilitychange"));
    run(16 * 2);
    expect(context.arc.mock.calls.length).toBeGreaterThan(hidden);
  });

  it("gives up animating after a run of slow frames and keeps the still frame", () => {
    const context = stubContext();
    const { clock, run } = manualClock();
    render(
      <AmbientField
        lights={LIGHTS}
        seed={1}
        hue="#6e3ceb"
        inks={INKS}
        clock={clock}
      />,
    );
    run(80 * 6, 80);
    const slowed = context.arc.mock.calls.length;
    run(16 * 10);
    expect(context.arc.mock.calls).toHaveLength(slowed);
  });
});
