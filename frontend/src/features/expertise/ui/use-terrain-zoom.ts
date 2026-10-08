import { type RefObject, useCallback, useEffect, useState } from "react";

import { clampZoom, ZOOM_STEP } from "@/features/expertise/ui/terrain-aim";

/**
 * Zooming the drawing with the wheel.
 *
 * The listener is native and non-passive on purpose: React's synthetic `onWheel` is passive, so `preventDefault`
 * there warns and the page scrolls out from under the reader instead of the map zooming.
 *
 * A bare wheel zooms, with no modifier to discover. On any screen with a wheel the drawing takes a share of the
 * viewport rather than sitting in a card the page scrolls past, so a wheel over it is a wheel over the map and
 * nothing else; the two zoom buttons stay the route that needs no mouse at all.
 *
 * `zoomBy` is for a pinch, which reports a ratio against wherever the zoom has got to rather than a level.
 */
export function useTerrainZoom(ref: RefObject<HTMLElement | null>): {
  zoom: number;
  setZoom: (next: number) => void;
  zoomBy: (factor: number) => void;
} {
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    const element = ref.current;
    if (element === null) return undefined;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      setZoom((now) =>
        clampZoom(event.deltaY < 0 ? now * ZOOM_STEP : now / ZOOM_STEP),
      );
    };
    element.addEventListener("wheel", onWheel, { passive: false });
    return () => element.removeEventListener("wheel", onWheel);
  }, [ref]);

  const zoomBy = useCallback(
    (factor: number) => setZoom((now) => clampZoom(now * factor)),
    [],
  );

  return { zoom, setZoom, zoomBy };
}
