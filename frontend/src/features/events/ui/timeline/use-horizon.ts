import { useCallback, useEffect, useState } from "react";

import { useSchedule } from "@/features/events/api/use-schedule";
import {
  addDaysIso,
  HORIZON_DAYS,
  HORIZON_STEP_DAYS,
  horizonDaysFor,
} from "@/features/events/domain/schedule-window";

/**
 * The schedule from today up to how far ahead the timeline has loaded. It starts a step ahead and
 * grows a step at a time, never past the horizon; the rows already loaded stay while it grows.
 */
export function useHorizon(slug: string, fromIso: string) {
  const [days, setDays] = useState(HORIZON_STEP_DAYS);
  const toIso = addDaysIso(fromIso, days);
  const scheduleQuery = useSchedule(slug, fromIso, toIso);
  const settled =
    scheduleQuery.data !== undefined && !scheduleQuery.isPlaceholderData;
  const [reachedMs, setReachedMs] = useState(() => new Date(fromIso).getTime());
  const toMs = new Date(toIso).getTime();
  if (settled && reachedMs !== toMs) setReachedMs(toMs);

  const reachForward = useCallback(
    (atMs: number) => {
      setDays((current) => Math.max(current, horizonDaysFor(fromIso, atMs)));
    },
    [fromIso],
  );
  const canGrow = settled && days < HORIZON_DAYS;
  // A new callback per step, so a list whose end is still in view after a step grows again.
  const grow = useCallback(() => {
    setDays(Math.min(days + HORIZON_STEP_DAYS, HORIZON_DAYS));
  }, [days]);

  return {
    scheduleQuery,
    reachedMs,
    reachForward,
    complete: settled && days >= HORIZON_DAYS,
    growWhenNear: canGrow ? grow : null,
  };
}

/**
 * Calls `grow` while the element sits within a screen of `<main>`'s bottom edge. Each grow
 * settles before the next, so a short schedule climbs to the horizon one step at a time.
 */
export function useGrowWhenNear(
  element: HTMLElement | null,
  grow: (() => void) | null,
) {
  const [near, setNear] = useState(false);

  useEffect(() => {
    if (element === null) return;
    const observer = new IntersectionObserver(
      (records) => {
        const last = records.at(-1);
        if (last !== undefined) setNear(last.isIntersecting);
      },
      { root: element.closest("main"), rootMargin: "0px 0px 100% 0px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [element]);

  useEffect(() => {
    if (near && grow !== null) grow();
  }, [near, grow]);
}
