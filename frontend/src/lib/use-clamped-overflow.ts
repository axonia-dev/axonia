import { type RefObject, useEffect, useState } from "react";

/**
 * Whether a line-clamped element is actually hiding text. CSS reports nothing about what
 * `line-clamp` cut off, so the only way to know is to measure the element against its own box.
 *
 * Re-measures on resize: a description that fits at a wide viewport is cut off at a narrow one,
 * so the answer belongs to the current layout, not to the first paint.
 *
 * Measuring stops while `clamped` is false, and the last answer stands. An expanded element is
 * unclamped and so never overflows - a hook that kept measuring would report "fits" and take away
 * the control the reader just used.
 */
export function useClampedOverflow(
  ref: RefObject<HTMLElement | null>,
  clamped: boolean,
): boolean {
  const [overflowing, setOverflowing] = useState(false);

  useEffect(() => {
    const element = ref.current;
    if (!element || !clamped) {
      return undefined;
    }
    const measure = () => {
      setOverflowing(element.scrollHeight > element.clientHeight);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref, clamped]);

  return overflowing;
}
