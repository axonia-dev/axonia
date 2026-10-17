import { centreOf, flyComet } from "@/features/recognition/ui/comet-trail";

type AnimateFn = (
  keyframes: Keyframe[],
  options: KeyframeAnimationOptions,
) => Animation;

const from = { x: 10, y: 200 };
const to = { x: 300, y: 40 };

function stubAnimate() {
  const animate = vi.fn<AnimateFn>(
    () =>
      ({
        finished: Promise.resolve(),
        cancel: vi.fn(),
      }) as unknown as Animation,
  );
  Element.prototype.animate = animate as unknown as Element["animate"];
  return animate;
}

afterEach(() => {
  delete (Element.prototype as { animate?: unknown }).animate;
  document.body.replaceChildren();
});

describe("flyComet", () => {
  it("flies four staggered aria-hidden hearts in one overlay", async () => {
    const animate = stubAnimate();
    const done = flyComet({ from, to, delay: 100 });
    const overlay = document.querySelector("[data-comet-overlay]")!;
    expect(overlay.parentElement).toBe(document.body);
    const spans = [...overlay.querySelectorAll("span")];
    expect(spans).toHaveLength(4);
    spans.forEach((s) => expect(s.getAttribute("aria-hidden")).toBe("true"));
    expect(animate.mock.calls.map(([, o]) => [o.delay, o.duration])).toEqual([
      [100, 640],
      [145, 640],
      [190, 640],
      [235, 640],
    ]);
    await done;
  });

  it("removes every heart once all flights settle", async () => {
    stubAnimate();
    await flyComet({ from, to });
    await vi.waitFor(() =>
      expect(
        document.querySelectorAll("[data-comet-overlay] span"),
      ).toHaveLength(0),
    );
  });

  it("resolves when the lead heart lands, before the trailing hearts finish", async () => {
    const releases: (() => void)[] = [];
    Element.prototype.animate = vi.fn(() => ({
      finished: new Promise<void>((resolve) => releases.push(resolve)),
    })) as unknown as Element["animate"];
    const done = flyComet({ from, to });
    releases[0]!();
    await done;
    expect(document.querySelectorAll("[data-comet-overlay] span")).toHaveLength(
      3,
    );
    releases.forEach((release) => release());
    await vi.waitFor(() =>
      expect(
        document.querySelectorAll("[data-comet-overlay] span"),
      ).toHaveLength(0),
    );
  });

  it("shares one overlay between concurrent flights", async () => {
    stubAnimate();
    const first = flyComet({ from, to });
    const second = flyComet({ from, to });
    expect(document.querySelectorAll("[data-comet-overlay]")).toHaveLength(1);
    await Promise.all([first, second]);
  });

  it("resolves and leaves no hearts when animate is unavailable", async () => {
    await expect(flyComet({ from, to })).resolves.toBeUndefined();
    expect(document.querySelectorAll("[data-comet-overlay] span")).toHaveLength(
      0,
    );
  });
});

describe("centreOf", () => {
  it("returns the middle of the bounding rect", () => {
    const el = document.createElement("div");
    el.getBoundingClientRect = () =>
      ({ left: 10, top: 20, width: 30, height: 40 }) as DOMRect;
    expect(centreOf(el)).toEqual({ x: 25, y: 40 });
  });
});
