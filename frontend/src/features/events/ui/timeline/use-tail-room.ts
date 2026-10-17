import { type RefObject, useLayoutEffect } from "react";

import { readingLine } from "@/features/events/ui/timeline/use-timeline-scroll";

/** How much room past the list lets `<main>` scroll its last row up to the reading line. */
function tailRoomIn(
  root: HTMLElement,
  main: HTMLElement,
  room: HTMLElement,
): number {
  const rows = root.querySelectorAll<HTMLElement>("[data-at]");
  const last = rows[rows.length - 1];
  if (last === undefined) return 0;
  const lastScroll =
    last.getBoundingClientRect().top - readingLine(main) + main.scrollTop;
  const maxScroll = main.scrollHeight - main.clientHeight;
  return Math.max(0, Math.ceil(room.offsetHeight + lastScroll - maxScroll));
}

/**
 * Sizes the `data-tail-room` element after a list that will not grow any more, so the lens can
 * still reach the sessions at its end: without it, the page bottoms out before the last rows come
 * under the ribbon. Set on the element in a layout effect rather than through state, so the room
 * is there before a scroll that waited for the list's last rows aims at them.
 */
export function useTailRoom(
  rootRef: RefObject<HTMLElement | null>,
  complete: boolean,
) {
  useLayoutEffect(() => {
    const root = rootRef.current;
    const main = root?.closest("main") ?? null;
    const room = root?.querySelector<HTMLElement>("[data-tail-room]") ?? null;
    if (root === null || main === null || room === null) return;
    if (!complete) {
      room.style.height = "0px";
      return;
    }
    const measure = () => {
      room.style.height = `${tailRoomIn(root, main, room)}px`;
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(main);
    observer.observe(root);
    return () => observer.disconnect();
  }, [rootRef, complete]);
}
