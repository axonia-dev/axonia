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

/** The ribbon's natural top inside `<main>`'s content: the ribbon opens the root, and unlike the ribbon the root never sticks. */
function ribbonTopIn(main: HTMLElement, root: HTMLElement | null): number {
  if (root === null) return 0;
  return (
    root.getBoundingClientRect().top -
    main.getBoundingClientRect().top +
    main.scrollTop
  );
}

/**
 * A target short of the ribbon's top would leave the page controls half cut, so it snaps to the
 * nearer of 0 and the ribbon's top. It never snaps past `memoryLimit`, the scroll that keeps the
 * newest memory just under the ribbon: hiding that memory loses the history above Today.
 */
function openingTop(
  target: number,
  ribbonTop: number,
  memoryLimit: number,
): number {
  if (target <= 0 || target >= ribbonTop) return target;
  const up = target >= ribbonTop / 2 && ribbonTop <= memoryLimit;
  return up ? ribbonTop : 0;
}

/** The largest scroll that still shows the memory right above Today under the ribbon, or Infinity with none. */
function memoryLimitIn(
  main: HTMLElement,
  root: HTMLElement | null,
  today: HTMLElement,
): number {
  const rows = rowsOf(root);
  const newest = rows[rows.indexOf(today) - 1];
  const ribbon = root?.querySelector("[data-time-ribbon]");
  if (newest === undefined || ribbon === null || ribbon === undefined) {
    return Infinity;
  }
  return (
    newest.getBoundingClientRect().top -
    main.getBoundingClientRect().top +
    main.scrollTop -
    ribbon.getBoundingClientRect().height
  );
}

function rowsOf(root: HTMLElement | null): HTMLElement[] {
  return root === null
    ? []
    : [...root.querySelectorAll<HTMLElement>("[data-at]")];
}

const atOf = (row: HTMLElement) => Number(row.dataset.at);

/** The row being read: the last one whose top is above the reading line, else the first. */
function readRow(
  root: HTMLElement | null,
  scroller: HTMLElement,
): HTMLElement | null {
  const rows = rowsOf(root);
  const line = readingLine(scroller);
  const read = rows.filter((row) => row.getBoundingClientRect().top <= line);
  return read.at(-1) ?? rows[0] ?? null;
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
  const anchor = useRef<{ row: HTMLElement; top: number } | null>(null);

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
    main.scrollTop = openingTop(
      todayTop - main.clientHeight / 3,
      ribbonTopIn(main, rootRef.current),
      memoryLimitIn(main, rootRef.current, today),
    );
  }, [ready, restored, rootRef, scroller, todayRef]);

  // Memories landing above the row being read (an older page, a session that just ended) would
  // push it down; moving `<main>` by the same amount keeps the reader's place. It only reads
  // layout when the memories changed, never on the renders in between.
  useLayoutEffect(() => {
    const main = scroller();
    const held = anchor.current;
    if (main === null || held === null || !held.row.isConnected) return;
    const moved = held.row.getBoundingClientRect().top - held.top;
    if (moved !== 0) main.scrollTop += moved;
  }, [memoryCount, scroller]);

  useEffect(() => {
    const main = scroller();
    if (main === null) return;
    let frame: number | null = null;
    const measure = () => {
      frame = null;
      const row = readRow(rootRef.current, main);
      anchor.current =
        row === null ? null : { row, top: row.getBoundingClientRect().top };
      setLensMs(row === null ? null : atOf(row));
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
