import { useCallback, useEffect, useRef, useState } from "react";

import { useMediaQuery } from "@/lib/use-media-query";

export type BillboardRotation = {
  index: number;
  select: (index: number) => void;
  next: () => void;
  prev: () => void;
  paused: boolean;
  togglePaused: () => void;
  hold: () => void;
  release: () => void;
  nudge: () => void;
  /** Rotation applies at all: two slides or more, and motion allowed. */
  rotates: boolean;
  /** The timer is running toward the next slide right now. */
  advancing: boolean;
  intervalMs: number;
};

const BILLBOARD_INTERVAL_MS = 7000;
const BILLBOARD_RESUME_AFTER_MS = 12_000;

/**
 * Cycles through `count` slides with wrap-around. The person can pause it for good
 * (`togglePaused`), hold it while hovering or focusing (`hold`/`release`), or move it by
 * hand (`nudge`), which suspends auto-advance for `resumeAfterMs`. Inert under reduced
 * motion and below two slides; `select`, `next` and `prev` still work.
 */
export function useBillboardRotation(
  count: number,
  {
    intervalMs = BILLBOARD_INTERVAL_MS,
    resumeAfterMs = BILLBOARD_RESUME_AFTER_MS,
    initialIndex = 0,
  }: Readonly<{
    intervalMs?: number;
    resumeAfterMs?: number;
    /** The slide on show when it mounts; clamped to the slides there are. */
    initialIndex?: number | undefined;
  }> = {},
): BillboardRotation {
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [rawIndex, setIndex] = useState(() =>
    Math.max(0, Math.min(count - 1, initialIndex)),
  );
  const [paused, setPaused] = useState(false);
  const [held, setHeld] = useState(false);
  const [nudged, setNudged] = useState(false);
  const resumeTimer = useRef<number | undefined>(undefined);

  const index = count > 0 ? rawIndex % count : 0;
  const rotates = count > 1 && !reducedMotion;
  const advancing = rotates && !paused && !held && !nudged;

  useEffect(() => {
    if (!advancing) return undefined;
    const timer = window.setTimeout(() => {
      setIndex((index + 1) % count);
    }, intervalMs);
    return () => window.clearTimeout(timer);
  }, [advancing, index, count, intervalMs]);

  useEffect(() => () => window.clearTimeout(resumeTimer.current), []);

  const nudge = useCallback(() => {
    window.clearTimeout(resumeTimer.current);
    setNudged(true);
    resumeTimer.current = window.setTimeout(
      () => setNudged(false),
      resumeAfterMs,
    );
  }, [resumeAfterMs]);

  const step = (delta: number) => {
    if (count > 0) setIndex((index + delta + count) % count);
  };

  return {
    index,
    select: setIndex,
    next: () => step(1),
    prev: () => step(-1),
    paused,
    togglePaused: () => setPaused((current) => !current),
    hold: useCallback(() => setHeld(true), []),
    release: useCallback(() => setHeld(false), []),
    nudge,
    rotates,
    advancing,
    intervalMs,
  };
}
