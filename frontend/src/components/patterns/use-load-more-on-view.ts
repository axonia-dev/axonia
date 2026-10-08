import { type RefCallback, useEffect, useRef, useState } from "react";

/** More entries load because the foot of the list came into view, not because anything was
 *  clicked. `enabled` is the whole gate: a caller passes false while a page is in flight and for
 *  good once the last one has landed, and the observer is torn down each time it goes false, so a
 *  sentinel that stays on screen asks for exactly one page at a time instead of a burst.
 *
 *  `loadMore` is held in a ref rather than watched as a dependency. An inline arrow at the call
 *  site is a new function on every render, and re-arming the observer on each one re-fires it
 *  against a sentinel that is already on screen - measured at seven requests for five pages.
 *
 *  The root is the caller's own scrolling element, not the viewport: the caller's content sits
 *  inside the viewport at all times, so a viewport-rooted margin would buy no head start at all.
 *
 *  Root and sentinel are tracked as state, set by callback refs, rather than as plain refs read
 *  inside the effect. React Aria Components renders a combobox's children once into a hidden,
 *  disconnected tree to build the collection before the popover ever opens; a plain ref captured
 *  by an effect with stable deps would latch onto that throwaway node and never see the real one.
 *  State-backed refs make every attach - including the later, real one - trigger a re-render, so
 *  the effect re-arms against whichever node is actually on screen. */
export function useLoadMoreOnView(
  enabled: boolean,
  loadMore: () => void,
): {
  rootRef: RefCallback<HTMLElement>;
  sentinelRef: RefCallback<HTMLDivElement>;
} {
  const [root, setRoot] = useState<HTMLElement | null>(null);
  const [sentinel, setSentinel] = useState<HTMLDivElement | null>(null);
  const loadMoreRef = useRef(loadMore);
  // Synced in its own effect, declared first so it lands before the observer effect below re-reads
  // it. Assigning during render reaches a ref from where a render may not.
  useEffect(() => {
    loadMoreRef.current = loadMore;
  }, [loadMore]);
  useEffect(() => {
    if (sentinel === null || !enabled) return;
    const observer = new IntersectionObserver(
      (records) => {
        if (records.some((record) => record.isIntersecting)) {
          loadMoreRef.current();
        }
      },
      { root, rootMargin: "160px" },
    );
    observer.observe(sentinel);
    return () => {
      observer.disconnect();
    };
  }, [enabled, root, sentinel]);
  return { rootRef: setRoot, sentinelRef: setSentinel };
}
