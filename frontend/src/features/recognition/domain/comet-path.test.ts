import {
  cometControl,
  cometKeyframes,
} from "@/features/recognition/domain/comet-path";

describe("cometControl", () => {
  it("lifts the control point above both ends", () => {
    const c = cometControl({ x: 300, y: 400 }, { x: 40, y: 60 });
    expect(c.y).toBeLessThan(60);
  });

  it("lifts a longer flight higher", () => {
    const near = cometControl({ x: 0, y: 100 }, { x: 50, y: 100 });
    const far = cometControl({ x: 0, y: 100 }, { x: 500, y: 100 });
    expect(far.y).toBeLessThan(near.y);
  });
});

describe("cometKeyframes", () => {
  const frames = cometKeyframes({
    from: { x: 0, y: 0 },
    to: { x: 100, y: 0 },
    size: 14,
    steps: 10,
  });

  it("starts at the origin and ends at the target, centred on the glyph", () => {
    expect(frames[0]!.transform).toContain("translate(-7px, -7px)");
    expect(frames.at(-1)!.transform).toContain("translate(93px, -7px)");
  });

  it("holds full opacity until the last 12 percent, then fades out", () => {
    expect(frames[8]!.opacity).toBe(1);
    expect(frames.at(-1)!.opacity).toBe(0);
  });

  it("shrinks from 1.1 to 0.8", () => {
    expect(frames[0]!.transform).toContain("scale(1.1)");
    expect(frames.at(-1)!.transform).toContain("scale(0.8)");
  });

  it("defaults to 25 frames with ascending offsets", () => {
    const defaults = cometKeyframes({
      from: { x: 0, y: 0 },
      to: { x: 10, y: 10 },
      size: 10,
    });
    expect(defaults).toHaveLength(25);
    expect(defaults[0]!.offset).toBe(0);
    expect(defaults.at(-1)!.offset).toBe(1);
  });
});
