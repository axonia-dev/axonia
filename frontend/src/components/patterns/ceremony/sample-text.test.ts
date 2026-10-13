import { afterEach, describe, expect, it, vi } from "vitest";

import { loadTextFont, textTargets } from "./sample-text";

function stubRange(box: DOMRect, fragments: DOMRect[]): void {
  Object.assign(Range.prototype, {
    getBoundingClientRect: () => box,
    getClientRects: () => fragments,
  });
}

describe("textTargets", () => {
  it("answers no points where the browser has no canvas", () => {
    // given jsdom, whose canvas has no 2D context
    const frame = document.createElement("div");
    const text = document.createElement("h1");
    text.textContent = "Backend Guild";
    frame.append(text);

    // when
    const actual = textTargets(text, frame);

    // then
    expect(actual).toEqual([]);
  });

  it("fits each word to the box the browser measured for it", () => {
    // given
    const fillText = vi.fn();
    const scale = vi.fn();
    const context = {
      measureText: () => ({ width: 100 }),
      save: vi.fn(),
      restore: vi.fn(),
      translate: vi.fn(),
      scale,
      fillText,
      getImageData: () => ({
        width: 1,
        height: 1,
        data: new Uint8ClampedArray(4),
      }),
    };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      context as never,
    );
    const frame = document.createElement("div");
    frame.getBoundingClientRect = () => new DOMRect(0, 0, 400, 100);
    const text = document.createElement("h1");
    text.textContent = "Backend";
    frame.append(text);
    const rect = new DOMRect(10, 20, 150, 30);
    stubRange(rect, [rect]);

    // when
    textTargets(text, frame);

    // then
    expect(scale).toHaveBeenCalledWith(1.5, 1);
    expect(fillText).toHaveBeenCalledWith("Backend", 0, 0);
  });

  it("draws a word the browser broke across lines one character at a time", () => {
    // given
    const fillText = vi.fn();
    const context = {
      measureText: () => ({ width: 10 }),
      save: vi.fn(),
      restore: vi.fn(),
      translate: vi.fn(),
      scale: vi.fn(),
      fillText,
      getImageData: () => ({
        width: 1,
        height: 1,
        data: new Uint8ClampedArray(4),
      }),
    };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      context as never,
    );
    const frame = document.createElement("div");
    frame.getBoundingClientRect = () => new DOMRect(0, 0, 400, 100);
    const text = document.createElement("h1");
    text.textContent = "Ab";
    frame.append(text);
    stubRange(new DOMRect(0, 0, 10, 20), [new DOMRect(), new DOMRect()]);

    // when
    textTargets(text, frame);

    // then
    expect(fillText.mock.calls.map((call: unknown[]) => call[0])).toEqual([
      "A",
      "b",
    ]);
  });
});

describe("loadTextFont", () => {
  it("asks the document to load the element's font", async () => {
    // given
    const load = vi.fn().mockResolvedValue([]);
    Object.defineProperty(document, "fonts", {
      value: { load },
      configurable: true,
    });
    const text = document.createElement("h1");
    text.textContent = "Backend";
    document.body.append(text);

    // when
    await loadTextFont(text);

    // then
    expect(load).toHaveBeenCalledWith(expect.stringContaining("px"), "Backend");
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  document.body.innerHTML = "";
  Reflect.deleteProperty(document, "fonts");
  Reflect.deleteProperty(Range.prototype, "getBoundingClientRect");
  Reflect.deleteProperty(Range.prototype, "getClientRects");
});
