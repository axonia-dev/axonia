import { afterEach, describe, expect, it, vi } from "vitest";

import { loadTextFont, sampleStep, textTargets } from "./sample-text";

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
    const actual = textTargets(text, frame, 5);

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
      setTransform: vi.fn(),
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
    textTargets(text, frame, 5);

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
      setTransform: vi.fn(),
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
    textTargets(text, frame, 5);

    // then
    expect(fillText.mock.calls.map((call: unknown[]) => call[0])).toEqual([
      "A",
      "b",
    ]);
  });
});

describe("sampleStep", () => {
  function scaled(fontSize: string, scale: number) {
    const frame = document.createElement("div");
    Object.defineProperty(frame, "offsetWidth", { value: 400 });
    frame.getBoundingClientRect = () => new DOMRect(0, 0, 400 * scale, 100);
    const text = document.createElement("h1");
    text.style.fontSize = fontSize;
    frame.append(text);
    document.body.append(frame);
    return { text, frame };
  }

  it("grows with the size the text is drawn at", () => {
    // given
    const small = scaled("36px", 1);
    const large = scaled("36px", 2);

    // when
    const actual = [
      sampleStep(small.text, small.frame),
      sampleStep(large.text, large.frame),
    ];

    // then
    expect(actual[1]).toBeCloseTo((actual[0] ?? 0) * 2);
  });

  it("keeps a floor for tiny text", () => {
    // given
    const { text, frame } = scaled("4px", 1);

    // when
    const actual = sampleStep(text, frame);

    // then
    expect(actual).toBe(1.5);
  });
});

describe("textTargets density", () => {
  it("samples at the device's pixel density and answers screen pixels", () => {
    // given an opaque pixel at device (4, 2) on a density 2 screen
    vi.spyOn(window, "devicePixelRatio", "get").mockReturnValue(2);
    const data = new Uint8ClampedArray(8 * 4 * 4);
    data[(2 * 8 + 4) * 4 + 3] = 255;
    const context = {
      setTransform: vi.fn(),
      getImageData: () => ({ width: 8, height: 4, data }),
    };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      context as never,
    );
    const frame = document.createElement("div");
    frame.getBoundingClientRect = () => new DOMRect(0, 0, 4, 2);
    const text = document.createElement("h1");

    // when
    const actual = textTargets(text, frame, 0.5);

    // then
    expect(context.setTransform).toHaveBeenCalledWith(2, 0, 0, 2, 0, 0);
    expect(actual).toEqual([{ x: 2, y: 1 }]);
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
