import { afterEach, describe, expect, it, vi } from "vitest";

import { canvasColour, drawLightBar, drawSparks } from "./paint";

afterEach(() => {
  vi.restoreAllMocks();
});

function recordingContext() {
  const gradient = { addColorStop: vi.fn() };
  return {
    save: vi.fn(),
    restore: vi.fn(),
    beginPath: vi.fn(),
    arc: vi.fn(),
    fill: vi.fn(),
    fillRect: vi.fn(),
    createLinearGradient: vi.fn(() => gradient),
    globalAlpha: 1,
    globalCompositeOperation: "source-over",
    fillStyle: "",
  };
}

describe("drawSparks", () => {
  it("draws one dot per spark", () => {
    // given
    const context = recordingContext();
    const sparks = [
      { x: 1, y: 2, alpha: 0.5, size: 2, white: false },
      { x: 3, y: 4, alpha: 0.5, size: 2, white: true },
    ];

    // when
    drawSparks(context as never, sparks, "#8b5cf6");

    // then
    expect(context.arc).toHaveBeenCalledTimes(2);
    expect(context.restore).toHaveBeenCalledOnce();
  });
});

const bar = { x: 100, top: 0, height: 400 };

describe("drawLightBar", () => {
  it("draws nothing at no brightness", () => {
    // given
    const context = recordingContext();

    // when
    drawLightBar(context as never, bar, "#8b5cf6", 0);

    // then
    expect(context.fillRect).not.toHaveBeenCalled();
  });

  it("draws a halo and a core", () => {
    // given
    const context = recordingContext();

    // when
    drawLightBar(context as never, bar, "#8b5cf6", 1);

    // then
    expect(context.fillRect).toHaveBeenCalledTimes(2);
  });
});

describe("canvasColour", () => {
  it("answers the colour the browser resolves inside the scope", () => {
    // given
    const scope = document.createElement("div");
    vi.spyOn(window, "getComputedStyle").mockImplementation(
      (element) =>
        ({
          color: element.parentElement === scope ? "rgb(26, 166, 160)" : "",
        }) as CSSStyleDeclaration,
    );

    // when
    const actual = canvasColour("var(--community-accent)", scope);

    // then
    expect(actual).toBe("rgb(26, 166, 160)");
    expect(scope.childElementCount).toBe(0);
  });

  it("keeps the colour as given when the browser resolves nothing", () => {
    // given
    vi.spyOn(window, "getComputedStyle").mockReturnValue({
      color: "",
    } as CSSStyleDeclaration);

    // when
    const actual = canvasColour("#1aa6a0", document.createElement("div"));

    // then
    expect(actual).toBe("#1aa6a0");
  });
});
