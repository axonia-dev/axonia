import { type RefObject, useLayoutEffect, useState } from "react";

export interface GraphSize {
  width: number;
  height: number;
}

/**
 * Substituted whenever a measurement comes back non-positive: an element with no box yet, or an environment with
 * no layout engine at all (jsdom - see {@link useGraphSize}). Picked to match the graph's original fixed canvas,
 * so the fallback render still looks like a normal graph rather than a degenerate one.
 */
const FALLBACK_WIDTH = 820;
const FALLBACK_HEIGHT = 560;

/**
 * Measures an element's real rendered box via `ResizeObserver`, so a caller can lay out its content at 1:1 scale
 * instead of guessing a fixed canvas an unpredictable container then rescales. Returns `null` until the first
 * measurement lands - a caller must not run a layout against a null or zero size.
 *
 * Both dimensions are measured. Height used to be derived from width by a formula, which meant the drawing was the
 * same size on a laptop and on a 27-inch monitor and had no way to know how much room the page had actually given
 * it. CSS knows that and arithmetic here does not, so the rule moved to the stylesheet and this hook only reports
 * what the browser resolved.
 *
 * **The measured element's height must not come from its own content**, or the canvas sized from it feeds back into
 * it. The map view gives the box a share of the viewport and lets the drawing fill what is left.
 *
 * `useLayoutEffect` measures synchronously after the DOM commits and before the browser paints, so in a real
 * browser that first measurement lands before anyone sees a frame: there is no visible flash of an unmeasured
 * state. jsdom has no layout engine, so `getBoundingClientRect` always reports 0 there and the `ResizeObserver`
 * stub never fires a callback of its own; the effect still runs once on mount, sees the non-positive measurement,
 * and substitutes the fallbacks above so the component still renders something sane under test instead of getting
 * stuck at `null` forever.
 */
export function useGraphSize(
  ref: RefObject<HTMLElement | null>,
): GraphSize | null {
  const [size, setSize] = useState<GraphSize | null>(null);

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    const measure = () => {
      const box = element.getBoundingClientRect();
      setSize({
        width: box.width > 0 ? box.width : FALLBACK_WIDTH,
        height: box.height > 0 ? box.height : FALLBACK_HEIGHT,
      });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref]);

  return size;
}
