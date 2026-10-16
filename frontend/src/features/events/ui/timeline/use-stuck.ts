import { useEffect, useState } from "react";
import { flushSync } from "react-dom";

/**
 * Whether a `sticky top-0` element is stuck: the sentinel placed right above it has begun to
 * scroll up out of `<main>`, the one scroll container. Observed, so nothing runs per scrolled frame.
 * Both thresholds report, so a jump straight past the edge is never missed.
 */
export function useStuck(sentinel: Element | null): boolean {
  const [stuck, setStuck] = useState(false);
  useEffect(() => {
    if (sentinel === null) return;
    const observer = new IntersectionObserver(
      (records) => {
        const last = records.at(-1);
        if (last === undefined) return;
        const top = last.rootBounds?.top ?? -Infinity;
        // Rendered in the same task, so the cover is up before the next frame paints.
        flushSync(() => setStuck(last.boundingClientRect.top < top));
      },
      { root: sentinel.closest("main"), threshold: [0, 1] },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel]);
  return stuck;
}
