import { useEffect, useState, useSyncExternalStore } from "react";

import { useNow } from "@/lib/use-wake-at";

function subscribeVisibility(onChange: () => void): () => void {
  document.addEventListener("visibilitychange", onChange);
  return () => document.removeEventListener("visibilitychange", onChange);
}

/** Whether the tab is in front: a clock nobody can see has no reason to tick. */
export function usePageVisible(): boolean {
  return useSyncExternalStore(
    subscribeVisibility,
    () => document.visibilityState === "visible",
    () => true,
  );
}

/** Whether the element is on screen. Starts true, so a missing observer never freezes a clock. */
export function useOnScreen(element: Element | null): boolean {
  const [onScreen, setOnScreen] = useState(true);
  useEffect(() => {
    if (element === null) return;
    const observer = new IntersectionObserver((records) => {
      const last = records.at(-1);
      if (last !== undefined) setOnScreen(last.isIntersecting);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [element]);
  return onScreen;
}

/**
 * A clock private to its caller: it re-renders that caller alone, on each whole `stepMs`, and only
 * while `active`. The page-wide `useNow` stays for instants every reader shares, such as a
 * session starting; a per-second display would otherwise re-render the whole page each second.
 * It reads whichever of the two is later, so a clock that slept while hidden is never behind.
 */
export function useLocalClock(stepMs: number, active: boolean): Date {
  const pageNow = useNow();
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    if (!active) return;
    let timer = 0;
    const arm = (delayMs: number) => {
      timer = window.setTimeout(() => {
        setNow(new Date());
        arm(stepMs - (Date.now() % stepMs));
      }, delayMs);
    };
    arm(0);
    return () => window.clearTimeout(timer);
  }, [stepMs, active]);
  return now.getTime() > pageNow.getTime() ? now : pageNow;
}
