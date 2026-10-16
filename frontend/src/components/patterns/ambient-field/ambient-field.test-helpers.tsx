import { act, render } from "@testing-library/react";
import { vi } from "vitest";

import {
  AmbientField,
  type AmbientFieldProps,
} from "@/components/patterns/ambient-field/AmbientField";
import { manualClock } from "@/components/patterns/ceremony/ceremony-stage.test-helpers";

const LIGHTS = [
  {
    key: "join:0:0",
    position: 0.9,
    freshness: 1,
    size: "large" as const,
    blooms: false,
  },
];
const INKS = { upper: "accent", lower: "accent" } as const;

function stubContext() {
  const arc = vi.fn();
  const addColorStop = vi.fn();
  const context = {
    canvas: { width: 0, height: 0 },
    arc,
    addColorStop,
    save: vi.fn(),
    restore: vi.fn(),
    beginPath: vi.fn(),
    fill: vi.fn(),
    fillRect: vi.fn(),
    clearRect: vi.fn(),
    drawImage: vi.fn(),
    setTransform: vi.fn(),
    createRadialGradient: () => ({ addColorStop }),
    createLinearGradient: () => ({ addColorStop: vi.fn() }),
    globalCompositeOperation: "source-over",
    fillStyle: "",
  };
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
    context as never,
  );
  return context;
}

export function reducedMotion(matches: boolean) {
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

export function hideTab(hidden: boolean) {
  vi.spyOn(document, "visibilityState", "get").mockReturnValue(
    hidden ? "hidden" : "visible",
  );
  document.dispatchEvent(new Event("visibilitychange"));
}

/** Renders a field in a host, on a clock the test advances by hand. */
export function renderField(
  props: Partial<AmbientFieldProps> = {},
  size = { width: 320, height: 180 },
) {
  const context = stubContext();
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(
    size.width,
  );
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(
    size.height,
  );
  const { clock, run } = manualClock();
  const field = (overrides: Partial<AmbientFieldProps>) => (
    <div data-testid="host" style={{ position: "relative" }}>
      <AmbientField
        lights={LIGHTS}
        seed={1}
        hue="#6e3ceb"
        inks={INKS}
        clock={clock}
        {...overrides}
      />
    </div>
  );
  const view = render(field(props));
  const frames = () => context.arc.mock.calls.length;
  const rerender = (overrides: Partial<AmbientFieldProps>) =>
    view.rerender(field({ ...props, ...overrides }));
  return { ...view, context, run, frames, rerender };
}

type Callback = (entries: never[]) => void;

/** Replaces a browser observer with one whose callback the test fires. */
export function captureObserver(
  name: "IntersectionObserver" | "ResizeObserver",
) {
  const captured: { fire: Callback } = { fire: () => undefined };
  class Captured {
    constructor(callback: Callback) {
      captured.fire = (entries) => act(() => callback(entries));
    }
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  vi.stubGlobal(name, Captured);
  return captured;
}
