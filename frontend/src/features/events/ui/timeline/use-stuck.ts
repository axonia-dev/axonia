import { useEffect, useState } from "react";

/**
 * Whether a `sticky top-0` element is stuck: the sentinel placed right above it has begun to
 * scroll up out of `<main>`, the one scroll container. Observed, so nothing runs per scrolled frame.
 */
export function useStuck(sentinel: Element | null): boolean {
  const [stuck, setStuck] = useState(false);
  useEffect(() => {
    if (sentinel === null) return;
    const observer = new IntersectionObserver(
      (records) => {
        const last = records.at(-1);
        if (last === undefined) return;
        setStuck(
          last.boundingClientRect.top < (last.rootBounds?.top ?? -Infinity),
        );
      },
      { root: sentinel.closest("main"), threshold: 1 },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel]);
  return stuck;
}
