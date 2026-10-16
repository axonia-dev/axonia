import {
  type RefObject,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  NavigationType,
  useLocation,
  useNavigationType,
} from "react-router-dom";

import { restoresMainScroll } from "@/components/patterns/main-scroll";

interface Options {
  rootRef: RefObject<HTMLElement | null>;
  todayRef: RefObject<HTMLElement | null>;
  /** The first memories page and the schedule have both settled. */
  ready: boolean;
  memoryCount: number;
}

/** How far below the ribbon a row counts as the one being read. */
const READING_GAP_PX = 24;

function readingLine(scroller: HTMLElement): number {
  const ribbon = scroller.querySelector("[data-time-ribbon]");
  const ribbonHeight = ribbon?.getBoundingClientRect().height ?? 0;
  return scroller.getBoundingClientRect().top + ribbonHeight + READING_GAP_PX;
}

function rowsOf(root: HTMLElement | null): HTMLElement[] {
  return root === null
    ? []
    : [...root.querySelectorAll<HTMLElement>("[data-at]")];
}

const atOf = (row: HTMLElement) => Number(row.dataset.at);

function lensAt(
  root: HTMLElement | null,
  scroller: HTMLElement,
): number | null {
  const rows = rowsOf(root);
  const line = readingLine(scroller);
  const read = rows.filter((row) => row.getBoundingClientRect().top <= line);
  const row = read.at(-1) ?? rows[0];
  return row === undefined ? null : atOf(row);
}

function nearestRow(rows: HTMLElement[], atMs: number): HTMLElement | null {
  let best: HTMLElement | null = null;
  for (const row of rows) {
    if (
      best === null ||
      Math.abs(atOf(row) - atMs) < Math.abs(atOf(best) - atMs)
    ) {
      best = row;
    }
  }
  return best;
}

function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Positions, anchors and reads the `<main>` scroller the timeline lives in. */
export function useTimelineScroll({
  rootRef,
  todayRef,
  ready,
  memoryCount,
}: Readonly<Options>): {
  lensMs: number | null;
  scrollToAt: (atMs: number) => void;
  scroller: () => HTMLElement | null;
} {
  const navigationType = useNavigationType();
  const { key } = useLocation();
  const [restored] = useState(
    () => navigationType === NavigationType.Pop && restoresMainScroll(key),
  );
  const [lensMs, setLensMs] = useState<number | null>(null);
  const positioned = useRef(false);
  const previousCount = useRef(memoryCount);
  const previousHeight = useRef<number | null>(null);

  const scroller = useCallback(
    () => rootRef.current?.closest("main") ?? null,
    [rootRef],
  );

  // A passive effect, so it lands after the shell's own layout effect has reset `<main>` for a new page.
  useEffect(() => {
    if (!ready || positioned.current) return;
    positioned.current = true;
    const main = scroller();
    const today = todayRef.current;
    if (restored || main === null || today === null) return;
    const todayTop =
      today.getBoundingClientRect().top -
      main.getBoundingClientRect().top +
      main.scrollTop;
    main.scrollTop = todayTop - main.clientHeight / 3;
  }, [ready, restored, scroller, todayRef]);

  useLayoutEffect(() => {
    const main = scroller();
    if (main === null) return;
    if (
      memoryCount > previousCount.current &&
      previousHeight.current !== null
    ) {
      main.scrollTop += main.scrollHeight - previousHeight.current;
    }
    previousCount.current = memoryCount;
    previousHeight.current = main.scrollHeight;
  });

  useEffect(() => {
    const main = scroller();
    if (main === null) return;
    let frame: number | null = null;
    const measure = () => {
      frame = null;
      setLensMs(lensAt(rootRef.current, main));
    };
    const onScroll = () => {
      frame ??= requestAnimationFrame(measure);
    };
    main.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    return () => {
      main.removeEventListener("scroll", onScroll);
      if (frame !== null) cancelAnimationFrame(frame);
    };
  }, [ready, memoryCount, rootRef, scroller]);

  const scrollToAt = useCallback(
    (atMs: number) => {
      const main = scroller();
      const row = nearestRow(rowsOf(rootRef.current), atMs);
      if (main === null || row === null) return;
      main.scrollTo({
        top:
          main.scrollTop + row.getBoundingClientRect().top - readingLine(main),
        behavior: prefersReducedMotion() ? "auto" : "smooth",
      });
    },
    [rootRef, scroller],
  );

  return { lensMs, scrollToAt, scroller };
}
