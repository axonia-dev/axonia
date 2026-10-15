import { describe, expect, it, vi } from "vitest";

import {
  inkOf,
  paintFrame,
  rgbOf,
} from "@/components/patterns/ambient-field/paint-motes";

interface LinearGradient {
  readonly span: readonly number[];
  readonly stops: [number, string][];
}

function fakeContext() {
  const calls: string[] = [];
  const operations: string[] = [];
  const linear: LinearGradient[] = [];
  const context = {
    calls,
    operations,
    linear,
    set globalCompositeOperation(value: string) {
      operations.push(value);
    },
    get globalCompositeOperation() {
      return operations.at(-1) ?? "source-over";
    },
    fillStyle: "" as unknown,
    globalAlpha: 1,
    save: () => calls.push("save"),
    restore: () => calls.push("restore"),
    beginPath: () => calls.push("beginPath"),
    arc: () => calls.push("arc"),
    fill: () => calls.push("fill"),
    fillRect: () => calls.push("fillRect"),
    clearRect: () => calls.push("clearRect"),
    drawImage: () => calls.push("drawImage"),
    setTransform: () => calls.push("setTransform"),
    createRadialGradient: () => ({ addColorStop: vi.fn() }),
    createLinearGradient: (...span: number[]) => {
      const gradient: LinearGradient = { span, stops: [] };
      linear.push(gradient);
      return {
        addColorStop: (offset: number, colour: string) =>
          gradient.stops.push([offset, colour]),
      };
    },
  };
  return context;
}

function fakeScratch() {
  const context = fakeContext();
  const canvas = {
    width: 0,
    height: 0,
    getContext: () => context,
  } as unknown as HTMLCanvasElement;
  return { canvas, context };
}

const alphaOf = (colour: string) =>
  Number(colour.slice(colour.lastIndexOf(",") + 1, -1));

const VIOLET = [110, 60, 235] as const;
const BOX = { width: 720, height: 480 };
const SAMPLE = { x: 10, y: 10, radius: 3, alpha: 0.5 };

describe("rgbOf", () => {
  it("reads what the browser resolves a colour to", () => {
    expect(rgbOf("rgb(110, 60, 235)")).toEqual([110, 60, 235]);
    expect(rgbOf("rgba(1, 2, 3, 0.5)")).toEqual([1, 2, 3]);
  });

  it("falls back to black for anything it cannot read", () => {
    expect(rgbOf("var(--x)")).toEqual([0, 0, 0]);
  });
});

describe("inkOf", () => {
  it("draws the accent as it is, normally composited", () => {
    expect(inkOf("accent", VIOLET)).toEqual({
      rgb: [110, 60, 235],
      additive: false,
    });
  });

  it("lifts the light ink toward white and adds it", () => {
    const actual = inkOf("light", VIOLET);
    expect(actual.additive).toBe(true);
    expect(actual.rgb[0]).toBeGreaterThan(110);
    expect(actual.rgb[2]).toBeGreaterThanOrEqual(235);
  });
});

describe("paintFrame", () => {
  it("draws every mote once with one ink when both halves share it", () => {
    const context = fakeContext();
    const scratch = fakeScratch();
    const ink = inkOf("accent", VIOLET);

    paintFrame(context as never, {
      samples: [SAMPLE, SAMPLE],
      box: BOX,
      seam: 200,
      upper: ink,
      lower: ink,
      scratch: scratch.canvas,
      ratio: 1,
    });

    expect(context.calls.filter((call) => call === "arc")).toHaveLength(4);
    expect(context.calls).not.toContain("drawImage");
    expect(scratch.context.calls).toEqual([]);
  });

  it("composites two layers when the inks differ", () => {
    const context = fakeContext();
    const { canvas: scratch, context: scratchContext } = fakeScratch();

    paintFrame(context as never, {
      samples: [SAMPLE],
      box: BOX,
      seam: 200,
      upper: inkOf("light", VIOLET),
      lower: inkOf("accent", VIOLET),
      scratch,
      ratio: 1,
    });

    expect(context.calls.filter((call) => call === "drawImage")).toHaveLength(
      2,
    );
    expect(context.operations).toContain("lighter");
    expect(scratchContext.calls).toContain("clearRect");
    expect(scratch.width).toBe(0);
  });

  it("crossfades the two inks over 48 px centred on the seam", () => {
    const { canvas: scratch, context: scratchContext } = fakeScratch();

    paintFrame(fakeContext() as never, {
      samples: [SAMPLE],
      box: BOX,
      seam: 200,
      upper: inkOf("light", VIOLET),
      lower: inkOf("accent", VIOLET),
      scratch,
      ratio: 1,
    });

    const [upper, lower] = scratchContext.linear;
    expect(upper?.span).toEqual([0, 176, 0, 224]);
    expect(lower?.span).toEqual([0, 176, 0, 224]);
    expect(upper?.stops.map(([, colour]) => alphaOf(colour))).toEqual([1, 0]);
    expect(lower?.stops.map(([, colour]) => alphaOf(colour))).toEqual([0, 1]);
  });

  it("fades the field down the card: full past the seam, 0.4 below it, 0.14 at the bottom", () => {
    const context = fakeContext();
    const ink = inkOf("accent", VIOLET);

    paintFrame(context as never, {
      samples: [],
      box: BOX,
      seam: 240,
      upper: ink,
      lower: ink,
      scratch: fakeScratch().canvas,
      ratio: 1,
    });

    expect(context.operations).toContain("destination-in");
    const [fade] = context.linear;
    expect(fade?.span).toEqual([0, 0, 0, BOX.height]);
    const stops = fade?.stops.map(([offset, colour]) => [
      Number(offset.toFixed(4)),
      alphaOf(colour),
    ]);
    expect(stops).toEqual([
      [0, 1],
      [0.54, 1],
      [0.78, 0.4],
      [1, 0.14],
    ]);
  });
});
