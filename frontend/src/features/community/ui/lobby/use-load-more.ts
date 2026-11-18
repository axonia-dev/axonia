import { type RefObject, useEffect, useRef } from "react";

/**
 * Calls `onReach` when the returned sentinel comes within `rootMargin` of `root` (the viewport by
 * default). It re-arms each time `active` turns true again, so a sentinel still in view after a
 * page lands asks for the next one.
 */
export function useLoadMore<T extends Element>({
  active,
  onReach,
  root,
  rootMargin = "400px",
}: Readonly<{
  active: boolean;
  onReach: () => void;
  root?: RefObject<Element | null>;
  rootMargin?: string;
}>): RefObject<T | null> {
  const sentinel = useRef<T>(null);
  const latest = useRef(onReach);

  useEffect(() => {
    latest.current = onReach;
  });

  useEffect(() => {
    const element = sentinel.current;
    if (!active || element === null) return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) latest.current();
      },
      { root: root?.current ?? null, rootMargin },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [active, root, rootMargin]);

  return sentinel;
}
