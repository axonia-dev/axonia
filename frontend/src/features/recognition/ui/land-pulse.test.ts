import { landPulse } from "@/features/recognition/ui/land-pulse";

type AnimateFn = (
  keyframes: Keyframe[],
  options: KeyframeAnimationOptions,
) => Animation;

afterEach(() => {
  delete (Element.prototype as { animate?: unknown }).animate;
  document.body.replaceChildren();
});

function target(): HTMLElement {
  const el = document.createElement("button");
  el.getBoundingClientRect = () =>
    ({ left: 100, top: 50, width: 24, height: 24 }) as DOMRect;
  document.body.append(el);
  return el;
}

describe("landPulse", () => {
  it("bounces the target and shows then removes a ring around it", async () => {
    let release: () => void = () => undefined;
    const animate = vi.fn<AnimateFn>(
      () =>
        ({
          finished: new Promise<void>((r) => (release = r)),
        }) as unknown as Animation,
    );
    Element.prototype.animate = animate as unknown as Element["animate"];
    const el = target();
    landPulse(el);
    expect(animate).toHaveBeenCalledTimes(2);
    const ring = document.querySelector<HTMLElement>(
      "[data-comet-overlay] span",
    )!;
    expect(ring.style.left).toBe("96px");
    expect(ring.style.top).toBe("46px");
    expect(ring.style.width).toBe("32px");
    expect(ring.style.height).toBe("32px");
    release();
    await vi.waitFor(() =>
      expect(document.querySelector("[data-comet-overlay] span")).toBeNull(),
    );
  });

  it("does nothing when animate is unavailable", () => {
    landPulse(target());
    expect(document.querySelector("[data-comet-overlay]")).toBeNull();
  });
});
